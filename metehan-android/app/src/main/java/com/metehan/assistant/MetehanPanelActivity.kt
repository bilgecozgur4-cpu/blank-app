package com.metehan.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
        if (!CorePrefs.isSafeUrl(base)) {
            Toast.makeText(this, "Güvensiz çekirdek adresi engellendi", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val baseUri = Uri.parse(base)
        val web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(NativeActionBridge(this, web), "AndroidMetehan")

        val autoStart = intent.getBooleanExtra(EXTRA_AUTOSTART, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val sameOrigin = uri.scheme == baseUri.scheme && uri.host == baseUri.host && effectivePort(uri) == effectivePort(baseUri)
                if (!sameOrigin) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (autoStart) {
                    view.postDelayed({
                        view.evaluateJavascript("if (typeof connectRealtime === 'function' && !pc) connectRealtime();", null)
                    }, 500)
                }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val host = request.origin.host
                val safeOrigin = request.origin.scheme == "https" || host == "127.0.0.1" || host == "localhost"
                val micGranted = ContextCompat.checkSelfPermission(this@MetehanPanelActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                val wantsAudio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                if (safeOrigin && micGranted && wantsAudio) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    request.deny()
                }
            }
        }
        web.loadUrl(base + if (autoStart) "/?autostart=1" else "/")
    }

    private fun effectivePort(uri: Uri): Int {
        if (uri.port != -1) return uri.port
        return if (uri.scheme == "https") 443 else 80
    }
}
