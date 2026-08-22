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

// Äá»•i mÃ£ PIN nÃ y thÃ nh mÃ£ cá»§a báº¡n â€” dÃ¹ng Ä‘á»ƒ phá»¥ huynh thoÃ¡t app
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
        webView.webChromeClient = WebChromeClient() // cáº§n cho fullscreen video

        webView.loadUrl("file:///android_asset/player.html")

        // Long-press á»Ÿ gÃ³c trÃªn-trÃ¡i mÃ n hÃ¬nh 3 láº§n liÃªn tiáº¿p trong lÃºc app cháº¡y
        // sáº½ má»Ÿ há»™p thoáº¡i nháº­p PIN Ä‘á»ƒ thoÃ¡t (trÃ¡nh tráº» vÃ´ tÃ¬nh báº¥m trÃºng)
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
        input.hint = "Nháº­p mÃ£ PIN phá»¥ huynh"
        AlertDialog.Builder(this)
            .setTitle("ThoÃ¡t KidsTube")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString() == PARENT_PIN) {
                    finishAffinity()
                }
            }
            .setNegativeButton("Huá»·", null)
            .show()
    }

    // Cháº·n nÃºt Back há»‡ thá»‘ng â€” tráº» khÃ´ng thoÃ¡t ra ngoÃ i Ä‘Æ°á»£c
    override fun onBackPressed() {
        // khÃ´ng gá»i super -> nÃºt back bá»‹ vÃ´ hiá»‡u hoÃ¡ bÃªn trong app
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
