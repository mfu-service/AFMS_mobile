package dz.afms.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var webView by remember { mutableStateOf<WebView?>(null) }
            var isDesktopMode by remember { mutableStateOf(false) }
            var canGoBack by remember { mutableStateOf(false) }
            var mobileUserAgent by remember { mutableStateOf("") }
            val context = LocalContext.current

            val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

            val url = "https://ft-edugate.univ-eloued.dz/"
            LaunchedEffect(Unit) {
                if (mobileUserAgent.isEmpty()) {
                    mobileUserAgent = WebView(context).settings.userAgentString
                }
            }

            val fileChooser = remember { FileChooserHolder() }
            val fileChooserLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                fileChooser.deliver(result)
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { }

            LaunchedEffect(Unit) {
                val needed = buildList {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.CAMERA)
                    }
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (Build.VERSION.SDK_INT <= 28 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
                if (needed.isNotEmpty()) {
                    permissionLauncher.launch(needed.toTypedArray())
                }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            if (canGoBack) {
                                IconButton(onClick = { webView?.goBack() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_logo),
                                    contentDescription = "Logo",
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    " AFMS",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { 
                                webView?.loadUrl("https://ft-edugate.univ-eloued.dz/dashboard")
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Dashboard,
                                    contentDescription = "Go to Dashboard",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { 
                                isDesktopMode = !isDesktopMode
                                // التحديث سيتم تلقائياً عبر كتلة update في AndroidView
                                webView?.reload()
                            }) {
                                Icon(
                                    imageVector = if (isDesktopMode) Icons.Default.Smartphone else Icons.Default.Computer,
                                    contentDescription = "Toggle Desktop Mode",
                                    tint = if (isDesktopMode) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { innerPadding ->
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    factory = { _ ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val target = request?.url?.toString() ?: return false
                                    if (isLikelyFileDownload(target)) {
                                        enqueueHttpDownload(
                                            context,
                                            target,
                                            view?.settings?.userAgentString,
                                            null,
                                            request?.requestHeaders?.get("Accept"),
                                        )
                                        return true
                                    }
                                    return false
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    canGoBack = view?.canGoBack() == true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    canGoBack = view?.canGoBack() == true
                                    if (isDesktopMode) {
                                        // إجبار الموقع على عرض عرض الكمبيوتر عبر JavaScript
                                        view?.evaluateJavascript(
                                            "(function() { " +
                                            "   var meta = document.querySelector('meta[name=\"viewport\"]');" +
                                            "   if (meta) meta.setAttribute('content', 'width=1280, initial-scale=0.1');" +
                                            "   document.body.style.minWidth = '1280px';" +
                                            "   Object.defineProperty(navigator, 'platform', { get: function() { return 'Win32'; } });" +
                                            "   Object.defineProperty(navigator, 'userAgent', { get: function() { return '$desktopUserAgent'; } });" +
                                            "})();",
                                            null
                                        )
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onPermissionRequest(request: PermissionRequest) {
                                    request.grant(request.resources)
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>?,
                                    fileChooserParams: FileChooserParams?,
                                ): Boolean {
                                    return try {
                                        fileChooserLauncher.launch(
                                            fileChooser.start(context, filePathCallback, fileChooserParams)
                                        )
                                        true
                                    } catch (_: Exception) {
                                        fileChooser.cancel()
                                        false
                                    }
                                }

                                override fun onCreateWindow(
                                    view: WebView,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message,
                                ): Boolean {
                                    val transport = resultMsg.obj as WebView.WebViewTransport
                                    val popup = WebView(view.context).apply {
                                        @SuppressLint("SetJavaScriptEnabled")
                                        settings.javaScriptEnabled = true
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                v: WebView,
                                                request: WebResourceRequest,
                                            ): Boolean {
                                                val popupUrl = request.url.toString()
                                                if (isLikelyFileDownload(popupUrl)) {
                                                    enqueueHttpDownload(
                                                        context,
                                                        popupUrl,
                                                        view.settings.userAgentString,
                                                        null,
                                                        null,
                                                    )
                                                } else {
                                                    view.loadUrl(popupUrl)
                                                }
                                                return true
                                            }
                                        }
                                    }
                                    transport.webView = popup
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }

                            setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
                                val name = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                                when {
                                    downloadUrl.startsWith("data:") ->
                                        AfmsBridge(context).saveBase64(downloadUrl, name, mimeType)
                                    downloadUrl.startsWith("blob:") ->
                                        downloadBlobInWebView(this, downloadUrl, name, mimeType)
                                    else -> enqueueHttpDownload(
                                        context,
                                        downloadUrl,
                                        userAgent,
                                        contentDisposition,
                                        mimeType,
                                    )
                                }
                            }

                            addJavascriptInterface(AfmsBridge(context), "AfmsBridge")
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                @SuppressLint("SetJavaScriptEnabled")
                                javaScriptEnabled = true
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                loadWithOverviewMode = isDesktopMode
                                useWideViewPort = isDesktopMode
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                textZoom = 100
                            }
                            loadUrl(url)
                            webView = this
                        }
                    },
                    update = { view ->
                            view.settings.apply {
                                userAgentString = if (isDesktopMode) desktopUserAgent else mobileUserAgent
                                useWideViewPort = isDesktopMode
                                loadWithOverviewMode = isDesktopMode
                            }
                            // تعيين مقياس الصفحة الابتدائي (1 = 100%)
                            if (isDesktopMode) view.setInitialScale(1) else view.setInitialScale(0)
                    }
                )
            }

            BackHandler(enabled = webView?.canGoBack() == true) {
                webView?.goBack()
            }
        }
    }
}
