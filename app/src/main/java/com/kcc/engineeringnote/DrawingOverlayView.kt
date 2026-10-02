package com.kcc.engineeringnote

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.text.InputType
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import kotlin.math.*

class DrawingOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var toolMode: ToolMode = ToolMode.PEN
    var penColor: Int = Color.RED
    var penWidth: Float = 5f
    var onChanged: (() -> Unit)? = null
    var onStatus: ((String) -> Unit)? = null

    private var marks: MutableList<Markup> = mutableListOf()
    private val redoStack = mutableListOf<Markup>()
    private var working: Markup? = null

    fun setMarks(newMarks: MutableList<Markup>) {
        marks = newMarks
        redoStack.clear()
        invalidate()
    }

    fun getMarks(): MutableList<Markup> = marks

    fun undo() {
        if (marks.isNotEmpty()) {
            redoStack += marks.removeAt(marks.lastIndex)
            changed()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            marks += redoStack.removeAt(redoStack.lastIndex)
            changed()
        }
    }

    fun cleanLastStroke() {
        val i = marks.indexOfLast { it.type == MarkupType.PEN && it.points.size >= 4 }
        if (i < 0) {
            onStatus?.invoke("정리할 펜 선이 없습니다")
            return
        }
        val src = marks[i]
        val xs = src.points.map { it.x }
        val ys = src.points.map { it.y }
        val minX = xs.minOrNull() ?: return
        val maxX = xs.maxOrNull() ?: return
        val minY = ys.minOrNull() ?: return
        val maxY = ys.maxOrNull() ?: return
        val first = src.points.first()
        val last = src.points.last()
        val diag = hypot(maxX - minX, maxY - minY).coerceAtLeast(0.01f)
        val endDist = hypot(last.x - first.x, last.y - first.y)
        val ratio = abs(maxX - minX) / abs(maxY - minY).coerceAtLeast(0.01f)
        val newType = when {
            endDist < diag * 0.25f -> MarkupType.CLOUD
            ratio > 3.5f || ratio < 0.28f -> MarkupType.ARROW
            else -> MarkupType.RECT
        }
        val pts = when (newType) {
            MarkupType.ARROW -> mutableListOf(first, last)
            else -> mutableListOf(NPoint(minX, minY), NPoint(maxX, maxY))
        }
        marks[i] = Markup(type = newType, points = pts, color = src.color, width = src.width)
        onStatus?.invoke("펜 표시를 ${newType.name} 도형으로 정리했습니다")
        changed()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        marks.forEach { drawMarkup(canvas, it) }
        working?.let { drawMarkup(canvas, it) }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val isStylus = e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER

        // Pen/highlighter/eraser are stylus-only so a finger never creates accidental ink.
        if (!isStylus && toolMode in setOf(ToolMode.PEN, ToolMode.HIGHLIGHTER, ToolMode.ERASER)) return false

        val p = norm(e.x, e.y)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (toolMode == ToolMode.TEXT) {
                    requestText(p)
                    return true
                }
                if (toolMode == ToolMode.ERASER || e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER ||
                    (isStylus && (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0)) {
                    eraseAt(p)
                    return true
                }
                val type = when (toolMode) {
                    ToolMode.PEN -> MarkupType.PEN
                    ToolMode.HIGHLIGHTER -> MarkupType.HIGHLIGHTER
                    ToolMode.CLOUD -> MarkupType.CLOUD
                    ToolMode.ARROW -> MarkupType.ARROW
                    ToolMode.RECT -> MarkupType.RECT
                    else -> return true
                }
                working = Markup(type = type, points = mutableListOf(p), color = penColor, width = penWidth)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (toolMode == ToolMode.ERASER) eraseAt(p)
                else working?.let {
                    if (it.type == MarkupType.PEN || it.type == MarkupType.HIGHLIGHTER) it.points += p
                    else if (it.points.size == 1) it.points += p else it.points[1] = p
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                working?.let {
                    if (it.points.size == 1) it.points += p
                    marks += it
                    redoStack.clear()
                    working = null
                    changed()
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    private fun requestText(p: NPoint) {
        val edit = EditText(context).apply {
            hint = "코멘트 입력"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        val dlg = AlertDialog.Builder(context)
            .setTitle("텍스트 / 코멘트")
            .setView(edit)
            .setPositiveButton("입력") { _, _ ->
                val t = edit.text.toString().trim()
                if (t.isNotEmpty()) {
                    marks += Markup(type = MarkupType.TEXT, points = mutableListOf(p), text = t, color = penColor, width = penWidth)
                    changed()
                }
            }
            .setNegativeButton("취소", null)
            .create()
        dlg.setOnShowListener {
            edit.requestFocus()
            edit.postDelayed({
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
            }, 150)
        }
        dlg.show()
    }

    private fun eraseAt(p: NPoint) {
        val radius = 0.025f
        val idx = marks.indexOfLast { m ->
            m.points.any { hypot(it.x - p.x, it.y - p.y) < radius }
        }
        if (idx >= 0) {
            redoStack += marks.removeAt(idx)
            changed()
        }
    }

    private fun drawMarkup(canvas: Canvas, m: Markup) {
        if (m.points.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = m.color
            strokeWidth = m.width
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (m.type == MarkupType.HIGHLIGHTER) alpha = 80
        }
        when (m.type) {
            MarkupType.PEN, MarkupType.HIGHLIGHTER -> {
                val path = Path()
                val p0 = denorm(m.points.first())
                path.moveTo(p0.x, p0.y)
                m.points.drop(1).forEach { np -> denorm(np).also { path.lineTo(it.x, it.y) } }
                canvas.drawPath(path, paint)
            }
            MarkupType.RECT -> if (m.points.size >= 2) canvas.drawRect(rectOf(m.points[0], m.points[1]), paint)
            MarkupType.CLOUD -> if (m.points.size >= 2) drawCloud(canvas, rectOf(m.points[0], m.points[1]), paint)
            MarkupType.ARROW -> if (m.points.size >= 2) drawArrow(canvas, denorm(m.points[0]), denorm(m.points[1]), paint)
            MarkupType.TEXT -> {
                val pt = denorm(m.points[0])
                paint.style = Paint.Style.FILL
                paint.textSize = max(28f, m.width * 5f)
                m.text.split("\n").forEachIndexed { i, line ->
                    canvas.drawText(line, pt.x, pt.y + i * paint.textSize * 1.2f, paint)
                }
            }
        }
    }

    private fun drawCloud(c: Canvas, r: RectF, p: Paint) {
        val step = max(18f, min(r.width(), r.height()) / 7f)
        var x = r.left
        while (x < r.right) {
            c.drawCircle(x, r.top, step * .45f, p); c.drawCircle(x, r.bottom, step * .45f, p); x += step
        }
        var y = r.top
        while (y < r.bottom) {
            c.drawCircle(r.left, y, step * .45f, p); c.drawCircle(r.right, y, step * .45f, p); y += step
        }
    }

    private fun drawArrow(c: Canvas, a: PointF, b: PointF, p: Paint) {
        c.drawLine(a.x, a.y, b.x, b.y, p)
        val angle = atan2((b.y-a.y).toDouble(), (b.x-a.x).toDouble())
        val len = 28.0
        val a1 = angle + Math.PI * .82
        val a2 = angle - Math.PI * .82
        c.drawLine(b.x, b.y, (b.x + cos(a1)*len).toFloat(), (b.y + sin(a1)*len).toFloat(), p)
        c.drawLine(b.x, b.y, (b.x + cos(a2)*len).toFloat(), (b.y + sin(a2)*len).toFloat(), p)
    }

    private fun rectOf(a: NPoint, b: NPoint): RectF {
        val p1 = denorm(a); val p2 = denorm(b)
        return RectF(min(p1.x,p2.x), min(p1.y,p2.y), max(p1.x,p2.x), max(p1.y,p2.y))
    }

    private fun norm(x: Float, y: Float) = NPoint(x / width.coerceAtLeast(1), y / height.coerceAtLeast(1))
    private fun denorm(p: NPoint) = PointF(p.x * width, p.y * height)
    private fun changed() { invalidate(); onChanged?.invoke() }
}
