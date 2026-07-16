package com.rhuta.kask.ui.markdown

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

/**
 * Enhanced Markdown WebView with dynamic streaming support.
 * Exactly synchronized with AINI for flagship performance.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownMessage(
    content: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewHeight by remember { mutableIntStateOf(100) }
    var isPageLoaded by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (isPageLoaded) 1f else 0f,
        animationSpec = tween(400),
        label = "webViewAlpha"
    )
    
    // Maintain a single WebView instance
    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                allowFileAccess = false
                allowContentAccess = false
            }
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onHeightChanged(height: Int) {
                    webViewHeight = height
                }
                
                @android.webkit.JavascriptInterface
                fun copyToClipboard(text: String) {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Code", text)
                    clipboard.setPrimaryClip(clip)
                }
            }, "Android")
        }
    }

    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    // Capture the state of isPageLoaded from the client
    LaunchedEffect(webView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                isPageLoaded = true
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                isPageLoaded = true
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }
    }

    // Initial load
    LaunchedEffect(isDark) {
        isPageLoaded = false
        val initialHtml = if (content.isNotBlank()) MarkdownParser.parse(content) else ""
        
        webView.loadDataWithBaseURL(
            "https://appassets.androidplatform.net/",
            getHtmlTemplate(isDark, initialHtml),
            "text/html",
            "UTF-8",
            null
        )
    }

    // Dynamic updates - only run when page is ready
    LaunchedEffect(content, isPageLoaded) {
        if (isPageLoaded && content.isNotBlank()) {
            val htmlFragment = MarkdownParser.parse(content)
            val escapedHtml = JSONObject.quote(htmlFragment)
            webView.evaluateJavascript("if (window.updateContent) { updateContent($escapedHtml); }", null)
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
            .fillMaxWidth()
            .height(webViewHeight.dp)
            .alpha(alpha)
    )
}
