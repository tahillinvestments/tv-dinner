package com.example.joyfuliptv

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

class MainActivity : ComponentActivity() {
  private var webView: WebView? = null

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    WebView.setWebContentsDebuggingEnabled(true)

    val assetLoader = WebViewAssetLoader.Builder()
      .setDomain("appassets.androidplatform.net")
      .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
      .build()

    setContent {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
          WebView(context).apply {
            webView = this
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              databaseEnabled = true
              allowFileAccess = true
              allowContentAccess = true
              allowFileAccessFromFileURLs = true
              allowUniversalAccessFromFileURLs = true
              mediaPlaybackRequiresUserGesture = false
              javaScriptCanOpenWindowsAutomatically = false
              setSupportMultipleWindows(false)
              mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
              userAgentString = userAgentString + " JoyfulIPTVMobileApp"
            }
            webChromeClient = object : WebChromeClient() {
              override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
              }
              override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
              ): Boolean {
                // Block popup window creation from third-party scripts/iframes
                return false
              }
            }
            webViewClient = object : WebViewClient() {
              override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
              ): Boolean {
                val url = request?.url ?: return false
                if (request.isForMainFrame && url.host != "appassets.androidplatform.net") {
                  // Block top-level navigation hijack from iframe popups
                  return true
                }
                return super.shouldOverrideUrlLoading(view, request)
              }

              override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
              ): WebResourceResponse? {
                val url = request?.url ?: return null
                if (url.host == "appassets.androidplatform.net") {
                  val response = assetLoader.shouldInterceptRequest(url)
                  if (response != null) {
                    val path = url.path?.lowercase() ?: ""
                    if (path.endsWith(".js") || path.endsWith(".mjs")) {
                      response.mimeType = "text/javascript"
                    } else if (path.endsWith(".css")) {
                      response.mimeType = "text/css"
                    } else if (path.endsWith(".json")) {
                      response.mimeType = "application/json"
                    } else if (path.endsWith(".svg")) {
                      response.mimeType = "image/svg+xml"
                    } else if (path.endsWith(".woff2")) {
                      response.mimeType = "font/woff2"
                    } else if (path.endsWith(".m3u") || path.endsWith(".m3u8")) {
                      response.mimeType = "application/vnd.apple.mpegurl"
                    }
                  }
                  return response
                }
                return super.shouldInterceptRequest(view, request)
              }
            }
            loadUrl("https://appassets.androidplatform.net/index.html")
          }
        }
      )
    }
  }

  override fun onBackPressed() {
    if (webView?.canGoBack() == true) {
      webView?.goBack()
    } else {
      super.onBackPressed()
    }
  }
}
