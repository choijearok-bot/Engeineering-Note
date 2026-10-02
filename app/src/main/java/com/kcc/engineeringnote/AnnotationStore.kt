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
        val root = JSONObject()
        root.put("documentUri", documentUri.toString())
        root.put("currentPage", currentPage)
        val pageObj = JSONObject()
        pages.forEach { (page, marks) ->
            val arr = JSONArray()
            marks.forEach { m ->
                val o = JSONObject()
                o.put("id", m.id)
                o.put("type", m.type.name)
                o.put("text", m.text)
                o.put("color", m.color)
                o.put("width", m.width.toDouble())
                val pts = JSONArray()
                m.points.forEach { p ->
                    pts.put(JSONArray().put(p.x.toDouble()).put(p.y.toDouble()))
                }
                o.put("points", pts)
                arr.put(o)
            }
            pageObj.put(page.toString(), arr)
        }
        root.put("pages", pageObj)
        file.writeText(root.toString())
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
                    val ptsArr = o.getJSONArray("points")
                    val pts = mutableListOf<NPoint>()
                    for (j in 0 until ptsArr.length()) {
                        val p = ptsArr.getJSONArray(j)
                        pts += NPoint(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
                    }
                    list += Markup(
                        id = o.optLong("id", System.nanoTime()),
                        type = MarkupType.valueOf(o.getString("type")),
                        points = pts,
                        text = o.optString("text", ""),
                        color = o.optInt("color", android.graphics.Color.RED),
                        width = o.optDouble("width", 5.0).toFloat()
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
