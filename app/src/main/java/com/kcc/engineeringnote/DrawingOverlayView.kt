package com.kcc.engineeringnote

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import kotlin.math.*

class DrawingOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var toolMode: ToolMode = ToolMode.PEN
    var penColor: Int = Color.RED
    var penWidth: Float = 5f
    var textSizePx: Float = 34f
    var onChanged: (() -> Unit)? = null
    var onStatus: ((String) -> Unit)? = null
    var nextCommentNumber: (() -> String)? = null
    var penOnlyMode: Boolean = true
    var pressureEnabled: Boolean = true
    var stylusButtonEraser: Boolean = true

    private var selectedId: Long? = null
    private var lastDragPoint: NPoint? = null
    private var marks: MutableList<Markup> = mutableListOf()
    private val redoStack = mutableListOf<Markup>()
    private var working: Markup? = null

    fun setMarks(newMarks: MutableList<Markup>) {
        marks = newMarks
        redoStack.clear()
        working = null
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
        marks[i] = src.copy(type = newType, points = pts)
        onStatus?.invoke("펜 표시를 ${newType.name} 도형으로 정리했습니다")
        changed()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        marks.forEach { drawMarkup(canvas, it) }
        working?.let { drawMarkup(canvas, it) }
        drawSelection(canvas)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (toolMode == ToolMode.READ) return false
        val isStylus = e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
        if (penOnlyMode && !isStylus && toolMode in setOf(ToolMode.PEN, ToolMode.HIGHLIGHTER, ToolMode.ERASER, ToolMode.LASSO)) return false

        val p = norm(e.x, e.y, e.pressure.coerceIn(0.1f, 1.5f))
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isStylus) requestUnbufferedDispatch(e)
                if (toolMode == ToolMode.LASSO) {
                    selectedId = findNearestMark(p)?.id
                    lastDragPoint = p
                    invalidate()
                    onStatus?.invoke(if (selectedId != null) "선택됨 · 드래그하여 이동" else "선택할 객체를 터치하세요")
                    return true
                }
                if (toolMode == ToolMode.TEXT) {
                    requestText(p, false)
                    return true
                }
                if (toolMode == ToolMode.COMMENT) {
                    requestText(p, true)
                    return true
                }
                if (toolMode == ToolMode.ERASER || e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER ||
                    (stylusButtonEraser && isStylus && (e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0)) {
                    eraseAt(p)
                    return true
                }
                val type = when (toolMode) {
                    ToolMode.PEN -> MarkupType.PEN
                    ToolMode.HIGHLIGHTER -> MarkupType.HIGHLIGHTER
                    ToolMode.CLOUD -> MarkupType.CLOUD
                    ToolMode.ARROW -> MarkupType.ARROW
                    ToolMode.LINE -> MarkupType.LINE
                    ToolMode.RECT -> MarkupType.RECT
                    ToolMode.ELLIPSE -> MarkupType.ELLIPSE
                    else -> return true
                }
                working = Markup(type = type, points = mutableListOf(p), color = penColor, width = penWidth, fontSize = textSizePx)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (toolMode == ToolMode.LASSO) {
                    moveSelected(p)
                    return true
                }
                if (toolMode == ToolMode.ERASER) eraseAt(p)
                else working?.let {
                    if (it.type == MarkupType.PEN || it.type == MarkupType.HIGHLIGHTER) it.points += p
                    else if (it.points.size == 1) it.points += p else it.points[1] = p
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (toolMode == ToolMode.LASSO) {
                    lastDragPoint = null
                    onChanged?.invoke()
                    return true
                }
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

    private fun requestText(p: NPoint, asComment: Boolean) {
        val edit = EditText(context).apply {
            hint = if (asComment) "Engineering comment" else "텍스트 입력"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
        }
        val category = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                listOf("General", "Piping", "Mechanical", "Civil", "Electrical", "Instrument", "Painting", "Document"))
        }
        val holder = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 0, 36, 0)
            addView(edit)
            if (asComment) addView(category)
        }
        val dlg = AlertDialog.Builder(context)
            .setTitle(if (asComment) "새 Comment" else "텍스트")
            .setView(holder)
            .setPositiveButton("입력") { _, _ ->
                val t = edit.text.toString().trim()
                if (t.isNotEmpty()) {
                    marks += Markup(
                        type = if (asComment) MarkupType.COMMENT else MarkupType.TEXT,
                        points = mutableListOf(p),
                        text = t,
                        color = penColor,
                        width = penWidth,
                        fontSize = textSizePx,
                        commentNo = if (asComment) nextCommentNumber?.invoke().orEmpty() else "",
                        category = if (asComment) category.selectedItem?.toString() ?: "General" else "General"
                    )
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

    private fun findNearestMark(p: NPoint): Markup? {
        val radius = 0.045f
        return marks.asReversed().firstOrNull { m ->
            m.points.any { hypot(it.x - p.x, it.y - p.y) < radius } ||
                (m.points.size >= 2 &&
                    p.x in min(m.points[0].x, m.points[1].x)..max(m.points[0].x, m.points[1].x) &&
                    p.y in min(m.points[0].y, m.points[1].y)..max(m.points[0].y, m.points[1].y))
        }
    }

    private fun moveSelected(p: NPoint) {
        val id = selectedId ?: return
        val prev = lastDragPoint ?: p
        val dx = p.x - prev.x
        val dy = p.y - prev.y
        marks.firstOrNull { it.id == id }?.let { mark ->
            mark.points.indices.forEach { i ->
                val q = mark.points[i]
                mark.points[i] = q.copy(
                    x = (q.x + dx).coerceIn(0f, 1f),
                    y = (q.y + dy).coerceIn(0f, 1f)
                )
            }
        }
        lastDragPoint = p
        invalidate()
    }

    private fun drawSelection(canvas: Canvas) {
        val id = selectedId ?: return
        val mark = marks.firstOrNull { it.id == id } ?: return
        if (mark.points.isEmpty()) return
        val xs = mark.points.map { it.x * width }
        val ys = mark.points.map { it.y * height }
        val pad = 14f
        val rect = RectF(
            (xs.minOrNull() ?: 0f) - pad,
            (ys.minOrNull() ?: 0f) - pad,
            (xs.maxOrNull() ?: 0f) + pad,
            (ys.maxOrNull() ?: 0f) + pad
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 150, 243)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
        }
        canvas.drawRect(rect, paint)
    }

    private fun eraseAt(p: NPoint) {
        val radius = 0.028f
        val idx = marks.indexOfLast { m -> m.points.any { hypot(it.x - p.x, it.y - p.y) < radius } }
        if (idx >= 0) {
            redoStack += marks.removeAt(idx)
            changed()
        }
    }

    private fun drawMarkup(canvas: Canvas, m: Markup) {
        if (m.points.isEmpty()) return
        when (m.type) {
            MarkupType.PEN, MarkupType.HIGHLIGHTER -> drawInk(canvas, m)
            MarkupType.RECT -> if (m.points.size >= 2) canvas.drawRect(rectOf(m.points[0], m.points[1]), strokePaint(m))
            MarkupType.ELLIPSE -> if (m.points.size >= 2) canvas.drawOval(rectOf(m.points[0], m.points[1]), strokePaint(m))
            MarkupType.LINE -> if (m.points.size >= 2) { val a = denorm(m.points[0]); val b = denorm(m.points[1]); canvas.drawLine(a.x, a.y, b.x, b.y, strokePaint(m)) }
            MarkupType.CLOUD -> if (m.points.size >= 2) drawCloud(canvas, rectOf(m.points[0], m.points[1]), strokePaint(m))
            MarkupType.ARROW -> if (m.points.size >= 2) drawArrow(canvas, denorm(m.points[0]), denorm(m.points[1]), strokePaint(m))
            MarkupType.TEXT -> drawText(canvas, m, false)
            MarkupType.COMMENT -> drawText(canvas, m, true)
        }
    }

    private fun drawInk(canvas: Canvas, m: Markup) {
        if (m.points.size == 1) return
        for (i in 1 until m.points.size) {
            val a = denorm(m.points[i - 1])
            val b = denorm(m.points[i])
            val pressure = if (pressureEnabled) ((m.points[i - 1].pressure + m.points[i].pressure) / 2f).coerceIn(0.25f, 1.6f) else 1f
            val p = strokePaint(m).apply {
                strokeWidth = if (m.type == MarkupType.HIGHLIGHTER) m.width * 2.2f else m.width * pressure
                if (m.type == MarkupType.HIGHLIGHTER) alpha = 75
            }
            canvas.drawLine(a.x, a.y, b.x, b.y, p)
        }
    }

    private fun strokePaint(m: Markup) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = m.color
        strokeWidth = m.width
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun drawText(canvas: Canvas, m: Markup, isComment: Boolean) {
        val pt = denorm(m.points[0])
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = m.color
            style = Paint.Style.FILL
            textSize = m.fontSize.coerceAtLeast(24f)
            isUnderlineText = m.underline
            isStrikeThruText = m.strike
            typeface = Typeface.create(Typeface.DEFAULT, when {
                m.bold && m.italic -> Typeface.BOLD_ITALIC
                m.bold -> Typeface.BOLD
                m.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            })
        }
        val prefix = if (isComment) "${m.commentNo} [${m.status.name}] " else ""
        val lines = (prefix + m.text).split("\n")
        if (isComment) {
            val maxWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
            val box = RectF(pt.x - 10, pt.y - paint.textSize, pt.x + maxWidth + 18, pt.y + lines.size * paint.textSize * 1.25f)
            canvas.drawRoundRect(box, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220,255,255,255); style = Paint.Style.FILL })
            canvas.drawRoundRect(box, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = m.color; style = Paint.Style.STROKE; strokeWidth = 2f })
        }
        lines.forEachIndexed { i, line -> canvas.drawText(line, pt.x, pt.y + i * paint.textSize * 1.2f, paint) }
    }

    private fun drawCloud(c: Canvas, r: RectF, p: Paint) {
        val step = max(18f, min(r.width(), r.height()) / 7f)
        var x = r.left
        while (x < r.right) { c.drawArc(RectF(x, r.top-step/2, min(x+step,r.right), r.top+step/2), 180f, 180f, false, p); x += step*0.75f }
        x = r.left
        while (x < r.right) { c.drawArc(RectF(x, r.bottom-step/2, min(x+step,r.right), r.bottom+step/2), 0f, 180f, false, p); x += step*0.75f }
        var y = r.top
        while (y < r.bottom) { c.drawArc(RectF(r.left-step/2, y, r.left+step/2, min(y+step,r.bottom)), 90f, 180f, false, p); y += step*0.75f }
        y = r.top
        while (y < r.bottom) { c.drawArc(RectF(r.right-step/2, y, r.right+step/2, min(y+step,r.bottom)), 270f, 180f, false, p); y += step*0.75f }
    }

    private fun drawArrow(c: Canvas, a: PointF, b: PointF, p: Paint) {
        c.drawLine(a.x, a.y, b.x, b.y, p)
        val ang = atan2((b.y-a.y).toDouble(), (b.x-a.x).toDouble())
        val len = 28f
        for (d in doubleArrayOf(2.55, -2.55)) {
            c.drawLine(b.x, b.y, (b.x + cos(ang+d)*len).toFloat(), (b.y + sin(ang+d)*len).toFloat(), p)
        }
    }

    private fun rectOf(a: NPoint, b: NPoint): RectF {
        val p1 = denorm(a); val p2 = denorm(b)
        return RectF(min(p1.x,p2.x), min(p1.y,p2.y), max(p1.x,p2.x), max(p1.y,p2.y))
    }

    private fun norm(x: Float, y: Float, pressure: Float = 1f) = NPoint((x/width.coerceAtLeast(1)).coerceIn(0f,1f), (y/height.coerceAtLeast(1)).coerceIn(0f,1f), pressure)
    private fun denorm(p: NPoint) = PointF(p.x*width, p.y*height)
    private fun changed() { invalidate(); onChanged?.invoke() }
}
