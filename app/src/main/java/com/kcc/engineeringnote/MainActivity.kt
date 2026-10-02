package com.kcc.engineeringnote

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.io.IOException
import java.util.ArrayDeque

class MainActivity : AppCompatActivity() {

    private lateinit var folderPanel: LinearLayout
    private lateinit var folderList: LinearLayout
    private lateinit var commentPanel: LinearLayout
    private lateinit var commentList: LinearLayout
    private lateinit var editorFrame: FrameLayout
    private lateinit var imageView: ImageView
    private lateinit var overlay: DrawingOverlayView
    private lateinit var status: TextView
    private lateinit var topBar: HorizontalScrollView
    private lateinit var toolBar: HorizontalScrollView

    private var rootUri: Uri? = null
    private var currentFolder: DocumentFile? = null
    private val folderStack = ArrayDeque<DocumentFile>()
    private var currentDocUri: Uri? = null
    private var currentMime: String? = null
    private var currentDocName: String = ""
    private var viewer: DocumentViewer? = null
    private var store: AnnotationStore? = null
    private var pageMarks = mutableMapOf<Int, MutableList<Markup>>()
    private var currentPage = 0
    private var readMode = false
    private var fullScreen = false

    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = object : Runnable {
        override fun run() {
            saveNow(false)
            autoSaveHandler.postDelayed(this, 5000)
        }
    }

    private val pickWorkspace = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            getPreferences(MODE_PRIVATE).edit().putString("workspace", uri.toString()).apply()
            setWorkspace(uri)
        }
    }

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importIntoCurrentFolder(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        val saved = getPreferences(MODE_PRIVATE).getString("workspace", null)
        if (saved != null) setWorkspace(Uri.parse(saved)) else pickWorkspace.launch(null)
        handleIncoming(intent)
        autoSaveHandler.postDelayed(autoSaveRunnable, 5000)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    override fun onPause() {
        super.onPause()
        saveNow(false)
    }

    override fun onDestroy() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        saveNow(false)
        viewer?.close()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(243,244,246))
        }

        fun styledButton(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text
            isAllCaps = false
            minWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { action() }
        }

        topBar = HorizontalScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.WHITE)
        }
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8,6,8,6)
        }
        topBar.addView(navBar)

        fun nav(text: String, action: () -> Unit) {
            navBar.addView(styledButton(text, action))
        }

        nav("☰ 파일") { toggle(folderPanel) }
        nav("작업폴더") { pickWorkspace.launch(rootUri) }
        nav("+ 폴더") { askNewFolder() }
        nav("삽입") { importFile.launch(arrayOf("application/pdf", "image/*")) }
        nav("검색") { askSearch() }
        nav("페이지") { pageManager() }
        nav("◀") { changePage(-1) }
        nav("▶") { changePage(1) }
        nav("Comments") { toggle(commentPanel); refreshComments() }
        nav("읽기") { toggleReadMode() }
        nav("전체화면") { toggleFullScreen() }

        toolBar = HorizontalScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(250,250,250))
        }
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8,6,8,6)
        }
        toolBar.addView(tools)

        fun tool(text: String, action: () -> Unit) {
            tools.addView(styledButton(text, action))
        }

        tool("✍ Pen") { setTool(ToolMode.PEN) }
        tool("🖍 Highlighter") { setTool(ToolMode.HIGHLIGHTER) }
        tool("⌫ Eraser") { setTool(ToolMode.ERASER) }
        tool("Lasso") { setTool(ToolMode.LASSO) }
        tool("☁ Cloud") { setTool(ToolMode.CLOUD) }
        tool("→ Arrow") { setTool(ToolMode.ARROW) }
        tool("╱ Line") { setTool(ToolMode.LINE) }
        tool("□ Box") { setTool(ToolMode.RECT) }
        tool("○ Circle") { setTool(ToolMode.ELLIPSE) }
        tool("T Text") { setTool(ToolMode.TEXT) }
        tool("💬 Comment") { setTool(ToolMode.COMMENT) }
        tool("● 얇은펜") { applyPenPreset(Color.BLACK, 3f) }
        tool("● 기본펜") { applyPenPreset(Color.BLACK, 5f) }
        tool("● 검토펜") { applyPenPreset(Color.RED, 6f) }
        tool("색상") { chooseColor() }
        tool("굵기") { chooseWidth() }
        tool("S Pen") { sPenSettings() }
        tool("↶") { overlay.undo() }
        tool("↷") { overlay.redo() }
        tool("✨ 정리") { overlay.cleanLastStroke() }

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        folderPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(12,12,12,12)
        }
        folderList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        folderPanel.addView(TextView(this).apply {
            text="프로젝트 / 카테고리"
            textSize=18f
            setTextColor(Color.BLACK)
            setPadding(8,8,8,8)
        })
        folderPanel.addView(ScrollView(this).apply { addView(folderList) }, LinearLayout.LayoutParams(dp(270), 0, 1f))

        editorFrame = FrameLayout(this).apply { setBackgroundColor(Color.rgb(75,75,75)) }
        imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(75,75,75))
        }
        overlay = DrawingOverlayView(this).apply {
            onChanged = { saveNow(true); refreshComments() }
            onStatus = { msg -> showStatus(msg) }
            nextCommentNumber = { allocateCommentNumber() }
        }
        editorFrame.addView(imageView, FrameLayout.LayoutParams(-1,-1))
        editorFrame.addView(overlay, FrameLayout.LayoutParams(-1,-1))

        commentPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(10,10,10,10)
            visibility = View.GONE
        }
        commentPanel.addView(TextView(this).apply {
            text="Comment List"
            textSize=18f
            setTextColor(Color.BLACK)
            setPadding(8,8,8,12)
        })
        commentList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        commentPanel.addView(ScrollView(this).apply { addView(commentList) }, LinearLayout.LayoutParams(dp(320), 0, 1f))

        body.addView(folderPanel, LinearLayout.LayoutParams(dp(290), -1))
        body.addView(editorFrame, LinearLayout.LayoutParams(0, -1, 1f))
        body.addView(commentPanel, LinearLayout.LayoutParams(dp(340), -1))

        status = TextView(this).apply {
            text = "작업폴더를 선택하세요"
            setPadding(16,8,16,8)
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.DKGRAY)
        }
        root.addView(topBar, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(toolBar, LinearLayout.LayoutParams(-1, dp(54)))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status, LinearLayout.LayoutParams(-1, dp(42)))
        setContentView(root)
    }

    private fun setWorkspace(uri: Uri) {
        rootUri = uri
        val root = DocumentFile.fromTreeUri(this, uri)
        currentFolder = root
        folderStack.clear()
        refreshFolder()
        showStatus("작업폴더 연결됨 · 실제 Galaxy Tab 폴더에 저장 · 자동저장 ON")
    }

    private fun refreshFolder(filter: String? = null) {
        folderList.removeAllViews()
        val folder = currentFolder ?: return
        TextView(this).apply {
            text = "현재: ${folder.name ?: "Workspace"}"
            setPadding(8,4,8,8)
        }.also { folderList.addView(it) }

        if (folderStack.isNotEmpty()) {
            Button(this).apply {
                text = "⬆ 상위 폴더"
                isAllCaps = false
                setOnClickListener {
                    currentFolder = folderStack.removeLast()
                    refreshFolder()
                }
            }.also { folderList.addView(it) }
        }

        val files = folder.listFiles().sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name?.lowercase() })
            .filter { filter.isNullOrBlank() || (it.name ?: "").contains(filter, ignoreCase = true) }
        files.forEach { f ->
            Button(this).apply {
                text = if (f.isDirectory) "📁 ${f.name}" else "📄 ${f.name}"
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener {
                    if (f.isDirectory) {
                        currentFolder?.let { folderStack.addLast(it) }
                        currentFolder = f
                        refreshFolder()
                        showStatus("폴더: ${f.name}")
                    } else openDocument(f.uri, f.type, f.name ?: "")
                }
            }.also { folderList.addView(it) }
        }
    }

    private fun askNewFolder() {
        val edit = EditText(this).apply { hint = "프로젝트명 또는 카테고리명" }
        AlertDialog.Builder(this).setTitle("새 폴더").setView(edit)
            .setPositiveButton("생성") { _, _ ->
                val name = edit.text.toString().trim()
                if (name.isNotEmpty()) {
                    currentFolder?.createDirectory(name)
                    refreshFolder()
                    showStatus("폴더 생성: $name")
                }
            }.setNegativeButton("취소", null).show()
    }

    private fun askSearch() {
        val edit = EditText(this).apply { hint = "현재 폴더 파일명 검색" }
        AlertDialog.Builder(this).setTitle("검색").setView(edit)
            .setPositiveButton("검색") { _, _ -> refreshFolder(edit.text.toString().trim()) }
            .setNeutralButton("전체 표시") { _, _ -> refreshFolder() }
            .setNegativeButton("취소", null).show()
    }

    private fun importIntoCurrentFolder(uris: List<Uri>) {
        val folder = currentFolder ?: return
        var imported = 0
        uris.forEach { src ->
            val mime = contentResolver.getType(src) ?: "application/octet-stream"
            val name = displayName(src) ?: if (mime == "application/pdf") "document.pdf" else "image"
            val target = folder.createFile(mime, name) ?: return@forEach
            try {
                contentResolver.openInputStream(src)?.use { input ->
                    contentResolver.openOutputStream(target.uri, "w")?.use { output -> input.copyTo(output) }
                }
                imported++
                if (uris.size == 1) openDocument(target.uri, mime, name)
            } catch (_: IOException) { }
        }
        refreshFolder()
        showStatus("파일 ${imported}개 삽입 완료 · 원본과 마크업 데이터 분리 저장")
    }

    private fun openDocument(uri: Uri, mime: String?, name: String = "") {
        val resolved = mime ?: contentResolver.getType(uri)
        val supported = resolved == "application/pdf" || resolved?.startsWith("image/") == true || uri.toString().lowercase().endsWith(".pdf")
        if (!supported) {
            showStatus("현재 v0.2에서는 PDF/JPG/PNG 작업을 지원합니다. Office 파일 미리보기는 다음 단계에서 추가합니다.")
            return
        }
        saveNow(false)
        currentDocUri = uri
        currentMime = resolved
        currentDocName = name.ifBlank { displayName(uri) ?: "Document" }
        viewer?.close()
        viewer = DocumentViewer(this).also { it.open(uri, currentMime) }
        store = AnnotationStore(this, uri)
        val loaded = store!!.load()
        pageMarks = loaded.first
        currentPage = loaded.second.coerceIn(0, (viewer?.pageCount ?: 1) - 1)
        renderCurrent()
        refreshComments()
    }

    private fun renderCurrent() {
        val v = viewer ?: return
        val w = editorFrame.width.coerceAtLeast(1600)
        Thread {
            val bmp = v.renderPage(currentPage, w)
            runOnUiThread {
                imageView.setImageBitmap(bmp)
                overlay.setMarks(pageMarks.getOrPut(currentPage) { mutableListOf() })
                showStatus("$currentDocName · Page ${currentPage+1}/${v.pageCount} · 자동저장 ON · S Pen 압력지원 · 버튼=순간 지우개")
            }
        }.start()
    }

    private fun changePage(delta: Int) {
        val v = viewer ?: return
        saveNow(false)
        val next = (currentPage + delta).coerceIn(0, v.pageCount - 1)
        if (next != currentPage) { currentPage = next; renderCurrent(); refreshComments() }
    }

    private fun pageManager() {
        val v = viewer ?: return
        val pages = Array(v.pageCount) { i -> "Page " + (i + 1) }
        AlertDialog.Builder(this)
            .setTitle("페이지 · " + (currentPage + 1) + "/" + v.pageCount)
            .setSingleChoiceItems(pages, currentPage) { dialog, which ->
                saveNow(false)
                currentPage = which
                renderCurrent()
                refreshComments()
                dialog.dismiss()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun saveNow(showIndicator: Boolean) {
        val s = store ?: return
        pageMarks[currentPage] = overlay.getMarks()
        s.save(pageMarks, currentPage)
        if (showIndicator && currentDocUri != null) {
            status.post { status.text = status.text.toString().substringBefore(" · 저장됨") + " · 저장됨 ✓" }
        }
    }

    private fun setTool(tool: ToolMode) {
        overlay.toolMode = tool
        readMode = tool == ToolMode.READ
        showStatus("${tool.name} 모드 · 변경 내용 자동저장")
    }

    private fun applyPenPreset(color: Int, width: Float) {
        overlay.penColor = color
        overlay.penWidth = width
        overlay.toolMode = ToolMode.PEN
        readMode = false
        showStatus("Pen preset 적용 · 굵기 " + width.toInt() + " · 자동저장")
    }

    private fun chooseColor() {
        val names = arrayOf("Red", "Blue", "Black", "Green", "Orange")
        val values = intArrayOf(Color.RED, Color.BLUE, Color.BLACK, Color.rgb(0,128,0), Color.rgb(255,128,0))
        AlertDialog.Builder(this).setTitle("펜/텍스트 색상").setItems(names) { _, which ->
            overlay.penColor = values[which]
            showStatus("색상: ${names[which]}")
        }.show()
    }

    private fun chooseWidth() {
        val names = arrayOf("1", "3", "5", "8", "12", "18")
        AlertDialog.Builder(this).setTitle("펜 굵기").setItems(names) { _, which ->
            overlay.penWidth = names[which].toFloat()
            overlay.textSizePx = 24f + names[which].toFloat() * 2f
            showStatus("펜 굵기: ${names[which]}")
        }.show()
    }

    private fun sPenSettings() {
        val labels = arrayOf("S Pen만 필기 (손가락은 화면 조작)", "필압으로 선 굵기 조절", "S Pen 버튼 = 순간 지우개")
        val checked = booleanArrayOf(overlay.penOnlyMode, overlay.pressureEnabled, overlay.stylusButtonEraser)
        AlertDialog.Builder(this)
            .setTitle("S Pen 설정")
            .setMultiChoiceItems(labels, checked) { _, which, value -> checked[which] = value }
            .setPositiveButton("적용") { _, _ ->
                overlay.penOnlyMode = checked[0]
                overlay.pressureEnabled = checked[1]
                overlay.stylusButtonEraser = checked[2]
                showStatus("S Pen 설정 적용 · 팜리젝션 우선 · 자동저장 ON")
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun allocateCommentNumber(): String {
        val maxNo = pageMarks.values.flatten().mapNotNull {
            if (it.commentNo.startsWith("C-")) it.commentNo.removePrefix("C-").toIntOrNull() else null
        }.maxOrNull() ?: 0
        return "C-${(maxNo + 1).toString().padStart(3, '0')}"
    }

    private fun allComments(): List<CommentRef> = pageMarks.flatMap { (page, list) ->
        list.filter { it.type == MarkupType.COMMENT }.map {
            CommentRef(page, it.id, it.commentNo.ifBlank { "C-???" }, it.text, it.category, it.status)
        }
    }.sortedWith(compareBy<CommentRef> { it.page }.thenBy { it.number })

    private fun refreshComments() {
        if (!::commentList.isInitialized) return
        commentList.removeAllViews()
        val comments = allComments()
        if (comments.isEmpty()) {
            commentList.addView(TextView(this).apply { text = "아직 등록된 Comment가 없습니다."; setPadding(8,8,8,8) })
            return
        }
        comments.forEach { c ->
            Button(this).apply {
                text = "${c.number} · P${c.page+1} · ${c.category} · ${c.status}\n${c.text}"
                isAllCaps = false
                gravity = Gravity.START
                setOnClickListener {
                    currentPage = c.page
                    renderCurrent()
                    showStatus("${c.number} 위치로 이동")
                }
                setOnLongClickListener {
                    cycleCommentStatus(c.markId)
                    true
                }
            }.also { commentList.addView(it) }
        }
    }

    private fun cycleCommentStatus(markId: Long) {
        pageMarks.values.flatten().firstOrNull { it.id == markId }?.let { mark ->
            mark.status = when (mark.status) {
                CommentStatus.OPEN -> CommentStatus.PENDING
                CommentStatus.PENDING -> CommentStatus.CLOSED
                CommentStatus.CLOSED -> CommentStatus.OPEN
            }
            saveNow(true)
            refreshComments()
            overlay.invalidate()
        }
    }

    private fun toggleReadMode() {
        readMode = !readMode
        overlay.toolMode = if (readMode) ToolMode.READ else ToolMode.PEN
        showStatus(if (readMode) "읽기 모드 · S Pen 마크업 잠금" else "편집 모드 · Pen")
    }

    private fun toggleFullScreen() {
        fullScreen = !fullScreen
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            if (fullScreen) window.insetsController?.hide(WindowInsets.Type.systemBars()) else window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (fullScreen) (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) else View.SYSTEM_UI_FLAG_VISIBLE
        }
        topBar.visibility = if (fullScreen) View.GONE else View.VISIBLE
        toolBar.visibility = if (fullScreen) View.GONE else View.VISIBLE
        status.visibility = if (fullScreen) View.GONE else View.VISIBLE
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            val flags = intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (flags != 0) try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
            openDocument(uri, intent.type)
        }
    }

    private fun displayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return null
    }

    private fun toggle(view: View) { view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
    private fun showStatus(text: String) { status.text = text }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
