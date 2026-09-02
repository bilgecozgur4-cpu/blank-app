package com.metehan.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings


data class AgentAction(val type: String, val label: String, val target: String)
data class AgentPlan(
    val reply: String,
    val confidence: Double,
    val needsConfirmation: Boolean,
    val action: AgentAction,
)

object ActionDispatcher {
    private val allowed = setOf("none", "open_url", "open_settings", "dial", "map_search", "share_text", "open_camera")

    fun execute(context: Context, action: AgentAction): Result<Unit> = runCatching {
        require(action.type in allowed) { "İzin verilmeyen eylem: ${action.type}" }
        when (action.type) {
            "none" -> Unit
            "open_url" -> {
                val uri = Uri.parse(action.target)
                require(uri.scheme == "https" || uri.scheme == "http") { "Yalnızca http/https adresleri açılabilir" }
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
            "open_settings" -> {
                val intent = when (action.target.lowercase()) {
                    "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                    "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    else -> Intent(Settings.ACTION_SETTINGS)
                }
                context.startActivity(intent)
            }
            "dial" -> {
                val number = action.target.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                require(number.isNotBlank()) { "Geçerli numara yok" }
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
            }
            "map_search" -> {
                require(action.target.isNotBlank()) { "Harita araması boş" }
                val uri = Uri.parse("geo:0,0?q=${Uri.encode(action.target)}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
            "share_text" -> {
                require(action.target.isNotBlank()) { "Paylaşılacak metin boş" }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, action.target)
                }
                context.startActivity(Intent.createChooser(intent, "METEHAN ile paylaş"))
            }
            "open_camera" -> context.startActivity(Intent(context, CameraVisionActivity::class.java))
        }
    }
}
