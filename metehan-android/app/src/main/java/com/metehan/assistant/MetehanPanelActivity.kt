package com.metehan.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MetehanPanelActivity : AppCompatActivity() {
    companion object { const val EXTRA_AUTOSTART = "autostart" }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val base = CorePrefs.coreUrl(this)
        if (!CorePrefs.isSafeUrl(base)) { Toast.makeText(this, "Güvensiz çekirdek adresi engellendi", Toast.LENGTH_LONG).show(); finish(); return }
        val web = WebView(this); setContentView(web)
        web.settings.javaScriptEnabled = true; web.settings.domStorageEnabled = true; web.settings.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val host = request.origin.host
                val safe = request.origin.scheme == "https" || host == "127.0.0.1" || host == "localhost"
                val mic = ContextCompat.checkSelfPermission(this@MetehanPanelActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (safe && mic) request.grant(request.resources) else request.deny()
            }
        }
        web.loadUrl(base + if (intent.getBooleanExtra(EXTRA_AUTOSTART, false)) "/?autostart=1" else "/")
    }
}
