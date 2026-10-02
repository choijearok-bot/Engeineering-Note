package com.kcc.engineeringnote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore

class DocumentViewer(private val context: Context) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var uri: Uri? = null
    var isPdf: Boolean = false
        private set

    val pageCount: Int get() = renderer?.pageCount ?: 1

    fun open(documentUri: Uri, mime: String?) {
        close()
        uri = documentUri
        isPdf = mime == "application/pdf" || documentUri.toString().lowercase().endsWith(".pdf")
        if (isPdf) {
            pfd = context.contentResolver.openFileDescriptor(documentUri, "r")
            renderer = pfd?.let { PdfRenderer(it) }
        }
    }

    fun renderPage(pageIndex: Int, targetWidth: Int): Bitmap? {
        val u = uri ?: return null
        return if (isPdf) {
            val r = renderer ?: return null
            val page = r.openPage(pageIndex.coerceIn(0, r.pageCount - 1))
            val w = targetWidth.coerceAtLeast(1200)
            val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bmp
        } else {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                val src = ImageDecoder.createSource(context.contentResolver, u)
                ImageDecoder.decodeBitmap(src) { decoder, _, _ -> decoder.isMutableRequired = true }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, u)
            }
        }
    }

    fun close() {
        renderer?.close(); renderer = null
        pfd?.close(); pfd = null
    }
}
