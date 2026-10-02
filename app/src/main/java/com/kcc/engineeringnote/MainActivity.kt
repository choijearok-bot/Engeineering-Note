package com.kcc.engineeringnote

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var folderPanel: LinearLayout
    private lateinit var folderList: LinearLayout
    private lateinit var editorFrame: FrameLayout
    private lateinit var imageView: ImageView
    private lateinit var overlay: DrawingOverlayView
    private lateinit var status: TextView

    private var rootUri: Uri? = null
    private var currentFolder: DocumentFile? = null
    private var currentDocUri: Uri? = null
    private var currentMime: String? = null
    private var viewer: DocumentViewer? = null
    private var store: AnnotationStore? = null
    private var pageMarks = mutableMapOf<Int, MutableList<Markup>>()
    private var currentPage = 0

    private val pickWorkspace = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            getPreferences(MODE_PRIVATE).edit().putString("workspace", uri.toString()).apply()
            setWorkspace(uri)
        }
    }

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importIntoCurrentFolder(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        val saved = getPreferences(MODE_PRIVATE).getString("workspace", null)
        if (saved != null) setWorkspace(Uri.parse(saved)) else pickWorkspace.launch(null)
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    override fun onPause() {
        super.onPause()
        saveNow()
    }

    override fun onDestroy() {
        viewer?.close()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(243,244,246)) }
        val top = HorizontalScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.WHITE) }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(8,8,8,8) }
        top.addView(bar)

        fun b(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text; isAllCaps = false; setOnClickListener { action() }
        }.also { bar.addView(it) }

        b("☰ 폴더") { folderPanel.visibility = if (folderPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        b("작업폴더") { pickWorkspace.launch(rootUri) }
        b("+ 폴더") { askNewFolder() }
        b("파일 삽입") { importFile.launch(arrayOf("application/pdf", "image/*")) }
        b("✍ Pen") { setTool(ToolMode.PEN) }
        b("Highlighter") { setTool(ToolMode.HIGHLIGHTER) }
        b("☁ Cloud") { setTool(ToolMode.CLOUD) }
        b("→ Arrow") { setTool(ToolMode.ARROW) }
        b("□ Box") { setTool(ToolMode.RECT) }
        b("⌨ Text") { setTool(ToolMode.TEXT) }
        b("Eraser") { setTool(ToolMode.ERASER) }
        b("↶") { overlay.undo() }
        b("↷") { overlay.redo() }
        b("✨ 정리") { overlay.cleanLastStroke() }
        b("◀ Page") { changePage(-1) }
        b("Page ▶") { changePage(1) }

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        folderPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(12,12,12,12)
        }
        folderList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(folderList) }
        folderPanel.addView(TextView(this).apply { text="프로젝트 / 카테고리"; textSize=18f; setTextColor(Color.BLACK); setPadding(8,8,8,16) })
        folderPanel.addView(scroll, LinearLayout.LayoutParams(dp(270), 0, 1f))

        editorFrame = FrameLayout(this).apply { setBackgroundColor(Color.rgb(75,75,75)) }
        imageView = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.rgb(75,75,75)) }
        overlay = DrawingOverlayView(this).apply {
            onChanged = { saveNow() }
            onStatus = { msg -> showStatus(msg) }
        }
        editorFrame.addView(imageView, FrameLayout.LayoutParams(-1,-1))
        editorFrame.addView(overlay, FrameLayout.LayoutParams(-1,-1))

        body.addView(folderPanel, LinearLayout.LayoutParams(dp(290), -1))
        body.addView(editorFrame, LinearLayout.LayoutParams(0, -1, 1f))

        status = TextView(this).apply {
            text = "작업폴더를 선택하세요"
            setPadding(16,8,16,8)
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.DKGRAY)
        }
        root.addView(top, LinearLayout.LayoutParams(-1, dp(64)))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status, LinearLayout.LayoutParams(-1, dp(40)))
        setContentView(root)
    }

    private fun setWorkspace(uri: Uri) {
        rootUri = uri
        currentFolder = DocumentFile.fromTreeUri(this, uri)
        refreshFolder()
        showStatus("작업폴더 연결됨 — 프로젝트/카테고리를 자유롭게 만드세요")
    }

    private fun refreshFolder() {
        folderList.removeAllViews()
        val folder = currentFolder ?: return
        Button(this).apply {
            text = "⬆ 상위 폴더"
            isAllCaps = false
            setOnClickListener {
                val root = rootUri?.let { DocumentFile.fromTreeUri(this@MainActivity, it) }
                if (root != null && folder.uri != root.uri) {
                    // SAF DocumentFile has no parent navigation. Reset to root; subfolders remain one tap away.
                    currentFolder = root
                    refreshFolder()
                }
            }
        }.also { folderList.addView(it) }
        folder.listFiles().sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name }).forEach { f ->
            Button(this).apply {
                text = if (f.isDirectory) "📁 ${f.name}" else "📄 ${f.name}"
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener {
                    if (f.isDirectory) { currentFolder = f; refreshFolder(); showStatus("폴더: ${f.name}") }
                    else openDocument(f.uri, f.type)
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

    private fun importIntoCurrentFolder(src: Uri) {
        val folder = currentFolder ?: return
        val mime = contentResolver.getType(src) ?: "application/octet-stream"
        val name = displayName(src) ?: if (mime == "application/pdf") "document.pdf" else "image"
        val target = folder.createFile(mime, name) ?: return
        try {
            contentResolver.openInputStream(src)?.use { input ->
                contentResolver.openOutputStream(target.uri, "w")?.use { output -> input.copyTo(output) }
            }
            refreshFolder()
            openDocument(target.uri, mime)
            showStatus("삽입 완료: $name — 모든 마크업은 자동저장됩니다")
        } catch (e: IOException) {
            showStatus("파일 삽입 실패: ${e.message}")
        }
    }

    private fun openDocument(uri: Uri, mime: String?) {
        saveNow()
        currentDocUri = uri
        currentMime = mime ?: contentResolver.getType(uri)
        viewer?.close()
        viewer = DocumentViewer(this).also { it.open(uri, currentMime) }
        store = AnnotationStore(this, uri)
        val loaded = store!!.load()
        pageMarks = loaded.first
        currentPage = loaded.second.coerceIn(0, (viewer?.pageCount ?: 1) - 1)
        renderCurrent()
    }

    private fun renderCurrent() {
        val v = viewer ?: return
        val w = editorFrame.width.coerceAtLeast(1600)
        Thread {
            val bmp = v.renderPage(currentPage, w)
            runOnUiThread {
                imageView.setImageBitmap(bmp)
                overlay.setMarks(pageMarks.getOrPut(currentPage) { mutableListOf() })
                showStatus("Page ${currentPage+1}/${v.pageCount} · 자동저장 ON · S Pen=필기, S Pen 버튼=순간 지우개")
            }
        }.start()
    }

    private fun changePage(delta: Int) {
        val v = viewer ?: return
        saveNow()
        val next = (currentPage + delta).coerceIn(0, v.pageCount - 1)
        if (next != currentPage) { currentPage = next; renderCurrent() }
    }

    private fun saveNow() {
        val s = store ?: return
        pageMarks[currentPage] = overlay.getMarks()
        s.save(pageMarks, currentPage)
        status.post { if (currentDocUri != null) status.text = status.text.toString().substringBefore(" · 저장됨") + " · 저장됨 ✓" }
    }

    private fun setTool(tool: ToolMode) {
        overlay.toolMode = tool
        showStatus("${tool.name} 모드 · 변경 내용 자동저장")
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

    private fun showStatus(text: String) { status.text = text }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
