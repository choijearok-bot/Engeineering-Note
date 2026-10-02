package com.kcc.engineeringnote

data class NPoint(val x: Float, val y: Float)

enum class MarkupType { PEN, HIGHLIGHTER, CLOUD, ARROW, RECT, TEXT }

data class Markup(
    val id: Long = System.nanoTime(),
    val type: MarkupType,
    val points: MutableList<NPoint> = mutableListOf(),
    var text: String = "",
    var color: Int = android.graphics.Color.RED,
    var width: Float = 5f
)

enum class ToolMode { PEN, HIGHLIGHTER, CLOUD, ARROW, RECT, TEXT, ERASER }
