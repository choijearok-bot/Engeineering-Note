package com.kcc.engineeringnote

import android.graphics.Color

data class NPoint(val x: Float, val y: Float, val pressure: Float = 1f)

enum class MarkupType { PEN, HIGHLIGHTER, CLOUD, ARROW, RECT, TEXT, COMMENT }
enum class CommentStatus { OPEN, PENDING, CLOSED }
enum class ToolMode { PEN, HIGHLIGHTER, CLOUD, ARROW, RECT, TEXT, COMMENT, ERASER, READ }

data class Markup(
    val id: Long = System.nanoTime(),
    val type: MarkupType,
    val points: MutableList<NPoint> = mutableListOf(),
    var text: String = "",
    var color: Int = Color.RED,
    var width: Float = 5f,
    var fontSize: Float = 30f,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false,
    var strike: Boolean = false,
    var commentNo: String = "",
    var category: String = "General",
    var status: CommentStatus = CommentStatus.OPEN
)

data class CommentRef(
    val page: Int,
    val markId: Long,
    val number: String,
    val text: String,
    val category: String,
    val status: CommentStatus
)
