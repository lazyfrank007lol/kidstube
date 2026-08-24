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

// Chi cho phep dung cac videoId nay
private val ALLOWED_VIDEO_IDS = setOf(
    "XqZsoesa55w",  // Baby Shark - Pinkfong
    "yCjJyiqpAuU",  // Twinkle Twinkle Little Star - SSS
    "l4WNrvVjiTw",  // The Wheels on the Bus - SSS
    "PoJBdOC6cLQ",  // Five Little Monkeys - SSS
    "e_04ZrNroTo",  // Old MacDonald - SSS
    "tpMH10dMoNY"   // Baa Baa Black Sheep - SSS
)

private val ALLOWED_HOSTS = listOf(
    "youtube.com", "m.youtube.com", "www.youtube.com",
    "googlevideo.com", "ytimg.com", "yt3.ggpht.com",
    "i.ytimg.com", "youtu.be", "accounts.google.com"
)

// JS inject de mask WebView fingerprint (YouTube dung de detect WebView)
private val MASK_WEBVIEW_JS = """
(function() {
    if (!window.chrome) {
        window.chrome = { runtime: {}, app: { isInstalled: false } };
    }
    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
})();
""".trimIndent()

// CSS inject de an thanh search, nav bar, goi y khi xem video
private val HIDE_YOUTUBE_UI_CSS = """
ytm-search-box, #search-form, ytm-pivot-bar-renderer,
#header-bar, .yt-core-attributed-string--link-inherit-color,
ytm-notification-action-renderer, ytm-compact-autoplay-renderer,
ytm-item-section-renderer:has(ytm-compact-autoplay-renderer),
.related-chips-bar-wrapper { display: none !important; }
""".trimIndent()

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

        // Bridge: grid HTML -> Android.playVideo(id)
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
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true

                // Cho phep cac domain YouTube infrastructure
                val isYouTubeDomain = ALLOWED_HOSTS.any { url.contains(it) }
                if (!isYouTubeDomain) return true // Chan

                // Neu la trang watch, kiem tra videoId co trong playlist khong
                if (url.contains("youtube.com/watch")) {
                    val videoId = Uri.parse(url).getQueryParameter("v")
                    if (videoId != null && !ALLOWED_VIDEO_IDS.contains(videoId)) {
                        // Video ngoai playlist -> quay ve grid ngay lap tuc
                        runOnUiThread { loadGrid() }
                        return true
                    }
                }

                // Block cac trang YouTube khac (home, search, channel...)
                val blockedPaths = listOf("/", "/feed", "/results", "/@", "/channel", "/shorts")
                val path = Uri.parse(url).path ?: ""
                if (blockedPaths.any { path == it || path.startsWith(it) }) {
                    runOnUiThread { loadGrid() }
                    return true
                }

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Inject JS truoc khi YouTube chay de mask WebView fingerprint
                view?.evaluateJavascript(MASK_WEBVIEW_JS, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url?.contains("youtube.com/watch") == true) {
                    // An UI khong can thiet cua YouTube
                    val escapedCss = HIDE_YOUTUBE_UI_CSS
                        .replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
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
        // O grid -> khong lam gi, tre khong thoat duoc
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