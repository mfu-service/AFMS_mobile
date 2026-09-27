package dz.afms.mobile

import android.app.Activity
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class FileChooserHolder {
    var callback: ValueCallback<Array<Uri>>? = null

    fun start(
        context: Context,
        filePathCallback: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?,
    ): Intent {
        callback?.onReceiveValue(null)
        callback = filePathCallback
        return Intent.createChooser(
            buildChooserIntent(params),
            context.getString(R.string.file_chooser_title),
        )
    }

    fun deliver(result: ActivityResult) {
        val pending = callback
        callback = null
        if (pending == null) return
        if (result.resultCode != Activity.RESULT_OK) {
            pending.onReceiveValue(null)
            return
        }
        val parsed = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        if (parsed != null) {
            pending.onReceiveValue(parsed)
            return
        }
        val data = result.data
        val clip = data?.clipData
        val uri = data?.data
        when {
            clip != null && clip.itemCount > 0 -> {
                pending.onReceiveValue(Array(clip.itemCount) { clip.getItemAt(it).uri })
            }
            uri != null -> pending.onReceiveValue(arrayOf(uri))
            else -> pending.onReceiveValue(null)
        }
    }

    fun cancel() {
        callback?.onReceiveValue(null)
        callback = null
    }
}

class AfmsBridge(private val context: Context) {
    @JavascriptInterface
    fun saveBase64(dataUrl: String?, fileName: String?, mimeType: String?) {
        if (dataUrl.isNullOrBlank()) return
        val comma = dataUrl.indexOf(',')
        val payload = if (comma >= 0) dataUrl.substring(comma + 1) else dataUrl
        val bytes = try {
            Base64.decode(payload, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            return
        }
        val mime = mimeType?.takeIf { it.isNotBlank() } ?: mimeFromDataUrl(dataUrl)
        val name = sanitizeFileName(fileName, mime)
        saveBytesToDownloads(context, bytes, name, mime)
    }
}

fun isLikelyFileDownload(url: String, mimeType: String? = null): Boolean {
    val mime = mimeType?.lowercase().orEmpty()
    if (DOWNLOAD_MIME_HINTS.any { mime.contains(it) }) return true
    val path = Uri.parse(url).path?.lowercase() ?: url.lowercase()
    return DOWNLOAD_EXTENSIONS.any { path.endsWith(it) }
}

fun enqueueHttpDownload(
    context: Context,
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?,
) {
    if (url.startsWith("blob:") || url.startsWith("data:")) return
    val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        val cookie = CookieManager.getInstance().getCookie(url)
        if (!cookie.isNullOrBlank()) {
            addRequestHeader("Cookie", cookie)
        }
        if (!userAgent.isNullOrBlank()) {
            addRequestHeader("User-Agent", userAgent)
        }
        setMimeType(mimeType)
        setTitle(name)
        setDescription(context.getString(R.string.download_in_progress))
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        setAllowedOverMetered(true)
        setAllowedOverRoaming(true)
    }
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
    toast(context, context.getString(R.string.download_started, name))
}

fun downloadBlobInWebView(webView: WebView, url: String, fileName: String?, mimeType: String?) {
    val safeUrl = url.replace("\\", "\\\\").replace("'", "\\'")
    val safeName = (fileName ?: "").replace("\\", "\\\\").replace("'", "\\'")
    val safeMime = (mimeType ?: "").replace("\\", "\\\\").replace("'", "\\'")
    webView.post {
        webView.evaluateJavascript(
            """
            (function() {
              fetch('$safeUrl').then(function(r) { return r.blob(); }).then(function(blob) {
                var reader = new FileReader();
                reader.onloadend = function() {
                  AfmsBridge.saveBase64(reader.result, '$safeName', blob.type || '$safeMime');
                };
                reader.readAsDataURL(blob);
              }).catch(function() {});
            })();
            """.trimIndent(),
            null,
        )
    }
}

private fun buildChooserIntent(params: WebChromeClient.FileChooserParams?): Intent {
    val accept = params?.acceptTypes
        ?.filter { it.isNotBlank() && it != "*/*" }
        .orEmpty()
    val mimeTypes = (accept + FILE_UPLOAD_MIME_TYPES).distinct().toTypedArray()
    val allowMultiple = params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE

    return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(
            Intent.EXTRA_MIME_TYPES,
            (mimeTypes.toList() + "application/octet-stream" + "*/*").distinct().toTypedArray(),
        )
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun saveBytesToDownloads(context: Context, bytes: ByteArray, fileName: String, mimeType: String) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("insert failed")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("stream failed")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            FileOutputStream(File(dir, fileName)).use { it.write(bytes) }
        }
        toast(context, context.getString(R.string.download_saved, fileName))
    } catch (_: Exception) {
        toast(context, context.getString(R.string.download_failed))
    }
}

private fun mimeFromDataUrl(dataUrl: String): String {
    val header = dataUrl.substringBefore(',').lowercase()
    return when {
        "pdf" in header -> "application/pdf"
        "spreadsheetml" in header || "excel" in header ->
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "csv" in header -> "text/csv"
        else -> "application/octet-stream"
    }
}

private fun sanitizeFileName(fileName: String?, mimeType: String): String {
    val decoded = try {
        URLDecoder.decode(fileName.orEmpty(), StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        fileName.orEmpty()
    }
    val cleaned = decoded.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .ifBlank { "AFMS_${System.currentTimeMillis()}" }
    val hasExt = cleaned.contains('.')
    if (hasExt) return cleaned
    val ext = when {
        mimeType.contains("pdf") -> ".pdf"
        mimeType.contains("spreadsheetml") || mimeType.contains("excel") -> ".xlsx"
        mimeType.contains("ms-excel") -> ".xls"
        mimeType.contains("csv") -> ".csv"
        else -> ""
    }
    return cleaned + ext
}

private fun toast(context: Context, message: String) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}

private val FILE_UPLOAD_MIME_TYPES = listOf(
    "application/pdf",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.oasis.opendocument.spreadsheet",
    "text/csv",
    "text/comma-separated-values",
)

private val DOWNLOAD_EXTENSIONS = listOf(
    ".pdf", ".xls", ".xlsx", ".csv", ".ods", ".doc", ".docx",
)

private val DOWNLOAD_MIME_HINTS = listOf(
    "pdf", "excel", "spreadsheet", "ms-excel", "csv",
)
