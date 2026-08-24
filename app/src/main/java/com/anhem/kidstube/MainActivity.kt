package com.anhem.kidstube

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

// Doi ma PIN nay thanh ma cua ban -- dung de phu huynh thoat app
private const val PARENT_PIN = "123123"

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var backPressCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)
        hideSystemBars()

        webView.settings.javaScriptEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.webChromeClient = WebChromeClient()

        // Fix YouTube Error 153: load voi base URL la https://www.youtube.com
        // thay vi file:// de YouTube IFrame API chap nhan embed
        val html = assets.open("player.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        webView.loadDataWithBaseURL(
            "https://www.youtube.com",
            html,
            "text/html",
            "UTF-8",
            null
        )

        // Long-press 3 lan lien tiep -> hien hop thoai nhap PIN phu huynh
        webView.setOnLongClickListener {
            backPressCount++
            if (backPressCount >= 3) {
                backPressCount = 0
                showParentGate()
            }
            true
        }
    }

    private fun showParentGate() {
        val input = EditText(this)
        input.hint = "Nhap ma PIN phu huynh"
        AlertDialog.Builder(this)
            .setTitle("Thoat KidsTube")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == PARENT_PIN) {
                    finishAffinity()
                }
            }
            .setNegativeButton("Huy", null)
            .show()
    }

    // Chan nut Back he thong -- tre khong thoat ra ngoai duoc
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // khong goi super -> nut back bi vo hieu hoa ben trong app
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