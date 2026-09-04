package com.metehan.assistant

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalActionRouter {
    private val tr = Locale("tr", "TR")

    fun route(message: String, device: JSONObject): AgentPlan? {
        val raw = message.trim()
        val text = raw.lowercase(tr)
        fun action(type: String, label: String, target: String) = AgentPlan(
            reply = "$label için hazır. Onay verirsen açacağım.",
            confidence = 0.98,
            needsConfirmation = true,
            action = AgentAction(type, label, target),
        )

        if (text.contains("wifi") && (text.contains("aç") || text.contains("ayar") || text.contains("ayarlar")))
            return action("open_settings", "Wi-Fi ayarlarını aç", "wifi")
        if ((text.contains("bluetooth") || text.contains("blutut")) && (text.contains("aç") || text.contains("ayar")))
            return action("open_settings", "Bluetooth ayarlarını aç", "bluetooth")
        if (text.contains("konum") && (text.contains("aç") || text.contains("ayar")))
            return action("open_settings", "Konum ayarlarını aç", "location")
        if ((text.contains("ayarları aç") || text == "ayarlar" || text == "ayarları aç"))
            return action("open_settings", "Android ayarlarını aç", "general")
        if (text.contains("kamerayı aç") || text.contains("kamera aç"))
            return action("open_camera", "METEHAN kamerasını aç", "")

        val url = Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(raw)?.value
        if (url != null) return action("open_url", "Bağlantıyı aç", url)

        val number = Regex("(?:\\+?90)?[0-9][0-9 ()-]{8,}").find(raw)?.value
            ?.replace(Regex("[^0-9+]"), "")
        if (number != null && (text.contains("ara") || text.contains("telefon")))
            return action("dial", "$number numarasını arama ekranına getir", number)

        if (text.startsWith("haritada ") || text.contains(" haritada ara")) {
            val q = raw
                .replace(Regex("(?i)haritada"), "")
                .replace(Regex("(?i)ara"), "")
                .trim()
            if (q.isNotBlank()) return action("map_search", "Haritada '$q' ara", q)
        }

        if (text.startsWith("paylaş ")) {
            val payload = raw.substringAfter(' ').trim()
            if (payload.isNotBlank()) return action("share_text", "Metni paylaş", payload)
        }

        if (text.contains("saat kaç") || text == "saat") {
            val now = SimpleDateFormat("HH:mm", tr).format(Date())
            return AgentPlan("Saat $now.", 1.0, false, AgentAction("none", "", ""))
        }

        if (text.contains("pil") && (text.contains("kaç") || text.contains("durum") || text.contains("yüzde"))) {
            val battery = device.optInt("battery_percent", -1)
            if (battery >= 0) return AgentPlan("Pil seviyesi %$battery.", 1.0, false, AgentAction("none", "", ""))
        }

        return null
    }
}
