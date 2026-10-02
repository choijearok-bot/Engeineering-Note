package com.kcc.engineeringnote

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AnnotationStore(private val context: Context, private val documentUri: Uri) {
    private val file: File by lazy {
        val safe = documentUri.toString().hashCode().toUInt().toString(16)
        File(context.filesDir, "reviews/$safe.json").also { it.parentFile?.mkdirs() }
    }

    fun save(pages: Map<Int, List<Markup>>, currentPage: Int) {
        val root = JSONObject().apply {
            put("documentUri", documentUri.toString())
            put("currentPage", currentPage)
            put("savedAt", System.currentTimeMillis())
        }
        val pageObj = JSONObject()
        pages.forEach { (page, marks) ->
            val arr = JSONArray()
            marks.forEach { m ->
                arr.put(JSONObject().apply {
                    put("id", m.id)
                    put("type", m.type.name)
                    put("text", m.text)
                    put("color", m.color)
                    put("width", m.width.toDouble())
                    put("fontSize", m.fontSize.toDouble())
                    put("bold", m.bold)
                    put("italic", m.italic)
                    put("underline", m.underline)
                    put("strike", m.strike)
                    put("commentNo", m.commentNo)
                    put("category", m.category)
                    put("status", m.status.name)
                    val pts = JSONArray()
                    m.points.forEach { p -> pts.put(JSONArray().put(p.x.toDouble()).put(p.y.toDouble()).put(p.pressure.toDouble())) }
                    put("points", pts)
                })
            }
            pageObj.put(page.toString(), arr)
        }
        root.put("pages", pageObj)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    fun load(): Pair<MutableMap<Int, MutableList<Markup>>, Int> {
        if (!file.exists()) return mutableMapOf<Int, MutableList<Markup>>() to 0
        return try {
            val root = JSONObject(file.readText())
            val pagesJson = root.optJSONObject("pages") ?: JSONObject()
            val result = mutableMapOf<Int, MutableList<Markup>>()
            val keys = pagesJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val arr = pagesJson.getJSONArray(key)
                val list = mutableListOf<Markup>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pts = mutableListOf<NPoint>()
                    val ptsArr = o.optJSONArray("points") ?: JSONArray()
                    for (j in 0 until ptsArr.length()) {
                        val p = ptsArr.getJSONArray(j)
                        pts += NPoint(p.optDouble(0, 0.0).toFloat(), p.optDouble(1, 0.0).toFloat(), p.optDouble(2, 1.0).toFloat())
                    }
                    list += Markup(
                        id = o.optLong("id", System.nanoTime()),
                        type = runCatching { MarkupType.valueOf(o.optString("type", "PEN")) }.getOrDefault(MarkupType.PEN),
                        points = pts,
                        text = o.optString("text", ""),
                        color = o.optInt("color", android.graphics.Color.RED),
                        width = o.optDouble("width", 5.0).toFloat(),
                        fontSize = o.optDouble("fontSize", 30.0).toFloat(),
                        bold = o.optBoolean("bold", false),
                        italic = o.optBoolean("italic", false),
                        underline = o.optBoolean("underline", false),
                        strike = o.optBoolean("strike", false),
                        commentNo = o.optString("commentNo", ""),
                        category = o.optString("category", "General"),
                        status = runCatching { CommentStatus.valueOf(o.optString("status", "OPEN")) }.getOrDefault(CommentStatus.OPEN)
                    )
                }
                result[key.toInt()] = list
            }
            result to root.optInt("currentPage", 0)
        } catch (_: Exception) {
            mutableMapOf<Int, MutableList<Markup>>() to 0
        }
    }
}
