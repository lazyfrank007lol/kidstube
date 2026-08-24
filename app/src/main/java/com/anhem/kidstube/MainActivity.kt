package com.anhem.kidstube

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

private const val PARENT_PIN = "123123"
private const val KIDSTUBE_SCHEME = "kidstube"

private val ALLOWED_VIDEO_IDS = setOf(
    "XqZsoesa55w", "yCjJyiqpAuU", "l4WNrvVjiTw",
    "PoJBdOC6cLQ", "e_04ZrNroTo", "tpMH10dMoNY"
)

private val ALLOWED_HOSTS = listOf(
    "youtube.com", "m.youtube.com", "www.youtube.com",
    "googlevideo.com", "ytimg.com", "yt3.ggpht.com",
    "i.ytimg.com", "youtu.be", "accounts.google.com"
)

private val BLOCKED_PATH_PREFIXES = listOf(
    "/feed", "/results", "/shorts", "/@", "/channel",
    "/playlist", "/hashtag", "/account", "/premium"
)

// Inject som truoc YouTube chay -- mask WebView fingerprint
// Su dung addDocumentStartJavaScript (API 29+) hoac onPageStarted
private const val MASK_JS = """
(function(){
  try {
    if(!window.chrome){
      window.chrome={
        runtime:{},
        app:{isInstalled:false,getDetails:function(){return null;}}
      };
    }
    Object.defineProperty(navigator,'webdriver',{get:()=>undefined});
    // Xoa dau hieu WebView
    delete window.Android;
    delete window._Android;
  } catch(e) {}
})();
"""

private const val HIDE_CSS = """
ytm-search-box,#search-form,ytm-pivot-bar-renderer,
#header-bar,ytm-notification-action-renderer,
ytm-compact-autoplay-renderer,.related-chips-bar-wrapper
{display:none!important}
"""

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var backPressCount = 0
    private var isOnGrid = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        hideSystemBars()

        webView.settings.apply {
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            // Chrome Mobile UA -- khong expose bat ky dau hieu WebView
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.6367.82 Mobile Safari/537.36"
        }

        // KHONG dung addJavascriptInterface -- window.Android se bi YouTube detect!
        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return true
                val scheme = uri.scheme ?: ""

                // ---------- Custom scheme: kidstube://play/VIDEO_ID ----------
                if (scheme == KIDSTUBE_SCHEME) {
                    val videoId = uri.host ?: return true  // kidstube://play/ID -> host = "play"
                    // path = /VIDEO_ID
                    val id = uri.path?.removePrefix("/") ?: return true
                    if (ALLOWED_VIDEO_IDS.contains(id)) {
                        isOnGrid = false
                        // Load YouTube truc tiep -- khong co window.Android expose
                        webView.loadUrl("https://www.youtube.com/watch?v=$id")
                    }
                    return true
                }

                // ---------- YouTube URLs ----------
                val host = uri.host ?: ""
                val isYouTube = ALLOWED_HOSTS.any { host.endsWith(it) }
                if (!isYouTube) return true  // Chan non-YouTube

                val path = uri.path ?: "/"

                // Trang xem video: kiem tra videoId co trong playlist khong
                if (path == "/watch") {
                    val videoId = uri.getQueryParameter("v")
                    return when {
                        videoId == null -> false
                        ALLOWED_VIDEO_IDS.contains(videoId) -> false  // OK
                        else -> { runOnUiThread { loadGrid() }; true }  // Ngoai playlist
                    }
                }

                // Chan trang chu YouTube va cac trang khong mong muon
                if (path == "/" && host.contains("youtube.com")) {
                    runOnUiThread { loadGrid() }
                    return true
                }
                if (BLOCKED_PATH_PREFIXES.any { path.startsWith(it) }) {
                    runOnUiThread { loadGrid() }
                    return true
                }

                return false  // Cho phep tat ca URL YouTube con lai
            }

            override fun onPageStarted(
                view: WebView?, url: String?, favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                // Inject mask NGAY KHI page bat dau load
                view?.evaluateJavascript(MASK_JS, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url?.contains("youtube.com/watch") == true) {
                    val css = HIDE_CSS.replace("'", "\\'").replace("\n", " ")
                    view?.evaluateJavascript(
                        "(function(){var s=document.createElement('style');" +
                        "s.textContent='$css';" +
                        "document.head&&document.head.appendChild(s);})()", null
                    )
                }
            }
        }

        loadGrid()

        webView.setOnLongClickListener {
            backPressCount++
            if (backPressCount >= 3) {
                backPressCount = 0
                showParentGate()
            }
            true
        }
    }

    private fun loadGrid() {
        isOnGrid = true
        val html = assets.open("player.html")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        webView.loadDataWithBaseURL(
            "https://www.youtube.com", html, "text/html", "UTF-8", null
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isOnGrid) enterPipMode()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        if (!isInPip) hideSystemBars()
    }

    private fun showParentGate() {
        val input = EditText(this)
        input.hint = "Nhap ma PIN phu huynh"
        AlertDialog.Builder(this)
            .setTitle("Thoat KidsTube")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == PARENT_PIN) finishAffinity()
            }
            .setNegativeButton("Huy", null).show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!isOnGrid) loadGrid()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                it.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}