package com.anhem.kidstube

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

private const val PARENT_PIN = "123123"

private val ALLOWED_VIDEO_IDS = setOf(
    "XqZsoesa55w", "yCjJyiqpAuU", "l4WNrvVjiTw",
    "PoJBdOC6cLQ", "e_04ZrNroTo", "tpMH10dMoNY"
)

// Domain duoc phep (YouTube infrastructure)
private val ALLOWED_HOSTS = listOf(
    "youtube.com", "m.youtube.com", "www.youtube.com",
    "googlevideo.com", "ytimg.com", "yt3.ggpht.com",
    "i.ytimg.com", "youtu.be", "accounts.google.com"
)

// Path YouTube bi chan (home, search, channel, shorts...)
// KHONG dung "/" o day vi startsWith("/") match moi thu!
private val BLOCKED_PATH_PREFIXES = listOf(
    "/feed", "/results", "/shorts", "/@", "/channel",
    "/playlist", "/hashtag", "/account", "/premium"
)

private const val MASK_JS = """
(function(){
  if(!window.chrome){
    window.chrome={runtime:{},app:{isInstalled:false}};
  }
  try{Object.defineProperty(navigator,'webdriver',{get:()=>undefined});}catch(e){}
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
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.6367.82 Mobile Safari/537.36"
        }
        webView.webChromeClient = WebChromeClient()

        webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun playVideo(videoId: String) {
                if (!ALLOWED_VIDEO_IDS.contains(videoId)) return
                runOnUiThread {
                    isOnGrid = false
                    webView.loadUrl("https://m.youtube.com/watch?v=$videoId")
                }
            }
        }, "Android")

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return true
                val url = uri.toString()
                val host = uri.host ?: ""
                val path = uri.path ?: "/"

                // Buoc 1: kiem tra domain co duoc phep khong
                val isAllowed = ALLOWED_HOSTS.any { host.endsWith(it) }
                if (!isAllowed) return true // Chan URL ngoai YouTube

                // Buoc 2: neu la trang /watch, kiem tra videoId
                if (path == "/watch") {
                    val videoId = uri.getQueryParameter("v")
                    return when {
                        videoId == null -> false // Cho phep (chua co v param)
                        ALLOWED_VIDEO_IDS.contains(videoId) -> false // Trong playlist -> OK
                        else -> { // Ngoai playlist -> kick ve grid
                            runOnUiThread { loadGrid() }
                            true
                        }
                    }
                }

                // Buoc 3: chan cac trang YouTube khong mong muon
                // Root path (trang chu YouTube)
                if (path == "/" && host.contains("youtube.com")) {
                    runOnUiThread { loadGrid() }
                    return true
                }
                // Cac path bi chan (search, channel, shorts,...)
                if (BLOCKED_PATH_PREFIXES.any { path.startsWith(it) }) {
                    runOnUiThread { loadGrid() }
                    return true
                }

                // Cho phep tat ca URL YouTube con lai (API calls, redirects, v.v.)
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view?.evaluateJavascript(MASK_JS, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url?.contains("youtube.com/watch") == true) {
                    val escapedCss = HIDE_CSS.replace("'", "\\'").replace("\n", " ")
                    view?.evaluateJavascript(
                        "(function(){var s=document.createElement('style');" +
                        "s.textContent='$escapedCss';" +
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