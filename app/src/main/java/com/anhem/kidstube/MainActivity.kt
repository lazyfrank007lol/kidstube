package com.anhem.kidstube

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.res.Configuration
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

// Domain duoc phep navigate trong WebView
private val ALLOWED_HOSTS = listOf(
    "youtube.com", "www.youtube.com", "m.youtube.com",
    "youtu.be", "googlevideo.com", "ytimg.com",
    "yt3.ggpht.com", "i.ytimg.com"
)

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var backPressCount = 0
    private var isOnGrid = true   // true = dang o man hinh grid, false = dang xem video

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
            // Chrome UA de YouTube chap nhan day du tinh nang
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.6367.82 Mobile Safari/537.36"
        }

        webView.webChromeClient = WebChromeClient()

        // Bridge: grid HTML goi Android.playVideo(id) -> navigate thang YouTube
        webView.addJavascriptInterface(object : Any() {
            @JavascriptInterface
            fun playVideo(videoId: String) {
                runOnUiThread {
                    isOnGrid = false
                    // Load YouTube mobile truc tiep -- KHONG dung embed/iframe
                    // -> khong co loi 150/152/153/154 vi day la YouTube that su
                    webView.loadUrl("https://m.youtube.com/watch?v=$videoId&autoplay=1")
                }
            }
        }, "Android")

        // Chi cho phep navigate trong YouTube -- chan moi URL khac
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true
                val allowed = ALLOWED_HOSTS.any { host ->
                    url.contains(host)
                }
                return !allowed  // true = chan, false = cho phep
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Inject CSS de an cac element khong can thiet tren YouTube
                if (url?.contains("youtube.com/watch") == true) {
                    val css = listOf(
                        "ytm-search-box",          // thanh search
                        "ytm-pivot-bar-renderer",  // bottom nav bar
                        "#header-bar",             // header
                        ".related-chips-bar-wrapper", // goi y
                        "ytm-notification-action-renderer"
                    ).joinToString(",") + "{display:none!important}"

                    view?.evaluateJavascript(
                        "(function(){" +
                        "var s=document.createElement('style');" +
                        "s.textContent='$css';" +
                        "document.head&&document.head.appendChild(s);" +
                        "})()", null
                    )
                }
            }
        }

        // Load grid
        loadGrid()

        // Long-press 3 lan -> PIN gate
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
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        webView.loadDataWithBaseURL(
            "https://www.youtube.com",
            html, "text/html", "UTF-8", null
        )
    }

    // Khi bam Home/Recents -> vao PiP neu dang xem video
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isOnGrid) enterPipMode()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPip: Boolean,
        newConfig: Configuration
    ) {
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
            .setNegativeButton("Huy", null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!isOnGrid) {
            // Dang xem video -> quay lai grid
            loadGrid()
        }
        // Neu o grid -> khong lam gi (tra em khong thoat duoc)
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