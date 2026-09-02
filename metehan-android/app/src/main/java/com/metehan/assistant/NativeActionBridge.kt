package com.metehan.assistant

import android.app.AlertDialog
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

class NativeActionBridge(
    private val activity: MetehanPanelActivity,
    private val webView: WebView,
) {
    private val allowed = setOf("open_url", "open_settings", "dial", "map_search", "share_text", "open_camera")

    @JavascriptInterface
    fun proposeAction(callId: String, actionJson: String) {
        if (callId.isBlank() || callId.length > 300 || actionJson.length > 12000) {
            callback(callId, JSONObject().put("ok", false).put("error", "Geçersiz native eylem isteği"))
            return
        }
        val parsed = runCatching { JSONObject(actionJson) }.getOrElse {
            callback(callId, JSONObject().put("ok", false).put("error", "Eylem JSON çözümlenemedi"))
            return
        }
        val action = AgentAction(
            type = parsed.optString("type", ""),
            label = parsed.optString("label", "").take(240),
            target = parsed.optString("target", "").take(4000),
        )
        if (action.type !in allowed) {
            callback(callId, JSONObject().put("ok", false).put("error", "İzin verilmeyen Android eylemi"))
            return
        }

        activity.runOnUiThread {
            val targetLine = action.target.takeIf { it.isNotBlank() }?.let { "\n\nHedef: $it" }.orEmpty()
            AlertDialog.Builder(activity)
                .setTitle("METEHAN eylem öneriyor")
                .setMessage("${action.label.ifBlank { action.type }}$targetLine\n\nBu eylem ancak sen onaylarsan çalışacak.")
                .setNegativeButton("Reddet") { _, _ ->
                    report(action, approved = false, executed = false, detail = "user_denied")
                    callback(callId, JSONObject().put("ok", false).put("denied_by_user", true).put("message", "Kullanıcı eylemi reddetti."))
                }
                .setPositiveButton("Onayla") { _, _ ->
                    val result = ActionDispatcher.execute(activity, action)
                    result.onSuccess {
                        report(action, approved = true, executed = true, detail = "ok")
                        callback(callId, JSONObject().put("ok", true).put("executed", true).put("action", action.type))
                    }.onFailure {
                        report(action, approved = true, executed = false, detail = it.message ?: "error")
                        callback(callId, JSONObject().put("ok", false).put("executed", false).put("error", it.message ?: "Eylem çalışmadı"))
                    }
                }
                .show()
        }
    }

    private fun report(action: AgentAction, approved: Boolean, executed: Boolean, detail: String) {
        Thread { runCatching { CoreClient(activity).reportAction(action, approved, executed, detail) } }.start()
    }

    private fun callback(callId: String, result: JSONObject) {
        activity.runOnUiThread {
            val script = "window.metehanNativeActionResult(${JSONObject.quote(callId)}, ${JSONObject.quote(result.toString())});"
            webView.evaluateJavascript(script, null)
        }
    }
}
