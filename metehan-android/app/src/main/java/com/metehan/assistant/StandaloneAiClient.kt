package com.metehan.assistant

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class StandaloneAiClient(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val db = MetehanLocalDb(context)

    private fun apiKey(): String = SecurePrefs.apiKey(context).ifBlank {
        error("OpenAI API anahtarı ayarlanmamış. Ana ekrandan API anahtarını kaydet.")
    }

    private fun requestBuilder(url: String = "https://api.openai.com/v1/responses"): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${apiKey()}")
            .header("Content-Type", "application/json")

    fun testConnection(): String {
        val req = requestBuilder("https://api.openai.com/v1/models").get().build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) error(apiError(r.code, text))
            return "API hazır · ${SecurePrefs.model(context)}"
        }
    }

    fun command(message: String, deviceContext: JSONObject): AgentPlan {
        val memory = db.relevantMemories(message, 8)
        val history = db.recentChat(8)

        val instructions = buildString {
            append("Sen METEHAN'sın: kullanıcının kişisel yapay zekâ başdanışmanı. Türkçe, net ve kanıta dayalı cevap ver. ")
            append("Kullanıcıya otomatik olarak katılma; gerekirse itiraz et. Önemli karar analizinde gözlem, çıkarım, belirsizlik ve karşı argümanı ayır. ")
            append("Bilmediğin şeyi uydurma; veri yetersizse açıkça söyle. Telefon eylemleri yalnızca öneridir ve uygulama ayrıca kullanıcı onayı ister. ")
            append("İzin verilen eylemler: none, open_url, open_settings, dial, map_search, share_text, open_camera. ")
            append("open_settings hedefi yalnız wifi, bluetooth, location veya general olmalı. Arama için yalnız numarayı dial hedefi yap; otomatik arama yapma. ")
            if (memory.isNotEmpty()) {
                append("\nİlgili yerel hafıza:\n")
                memory.forEach { append("- ").append(it).append('\n') }
            }
            append("\nTelefon bağlamı: ").append(deviceContext.toString())
        }

        val input = JSONArray()
        history.forEach { (role, text) ->
            input.put(JSONObject().put("role", if (role == "assistant") "assistant" else "user").put("content", text))
        }
        input.put(JSONObject().put("role", "user").put("content", message))

        val actionSchema = JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put("properties", JSONObject()
                .put("type", JSONObject().put("type", "string").put("enum", JSONArray(listOf("none", "open_url", "open_settings", "dial", "map_search", "share_text", "open_camera"))))
                .put("label", JSONObject().put("type", "string"))
                .put("target", JSONObject().put("type", "string")))
            .put("required", JSONArray(listOf("type", "label", "target")))

        val schema = JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put("properties", JSONObject()
                .put("reply", JSONObject().put("type", "string"))
                .put("confidence", JSONObject().put("type", "number").put("minimum", 0).put("maximum", 1))
                .put("needs_confirmation", JSONObject().put("type", "boolean"))
                .put("action", actionSchema))
            .put("required", JSONArray(listOf("reply", "confidence", "needs_confirmation", "action")))

        val body = JSONObject()
            .put("model", SecurePrefs.model(context))
            .put("instructions", instructions)
            .put("input", input)
            .put("reasoning", JSONObject().put("effort", "medium"))
            .put("text", JSONObject().put("format", JSONObject()
                .put("type", "json_schema")
                .put("name", "metehan_agent_plan")
                .put("strict", true)
                .put("schema", schema)))

        val root = post(body)
        val raw = extractOutputText(root)
        val json = JSONObject(raw)
        val actionJson = json.optJSONObject("action") ?: JSONObject()
        val action = AgentAction(
            type = actionJson.optString("type", "none"),
            label = actionJson.optString("label", ""),
            target = actionJson.optString("target", ""),
        )
        val allowed = setOf("none", "open_url", "open_settings", "dial", "map_search", "share_text", "open_camera")
        val safeAction = if (action.type in allowed) action else AgentAction("none", "", "")
        val plan = AgentPlan(
            reply = json.optString("reply", raw),
            confidence = json.optDouble("confidence", 0.5).coerceIn(0.0, 1.0),
            needsConfirmation = safeAction.type != "none",
            action = safeAction,
        )
        db.addChat("user", message)
        db.addChat("assistant", plan.reply)
        return plan
    }

    fun analyzeImage(file: File, prompt: String): String {
        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val content = JSONArray()
            .put(JSONObject().put("type", "input_text").put("text", prompt.ifBlank { "Bu görüntüyü analiz et." }))
            .put(JSONObject()
                .put("type", "input_image")
                .put("image_url", "data:image/jpeg;base64,$b64")
                .put("detail", "auto"))
        val body = JSONObject()
            .put("model", SecurePrefs.model(context))
            .put("instructions", "Sen METEHAN'sın. Görseli dikkatle analiz et; gördüğün şey ile çıkarımı ayır, belirsizliği belirt ve Türkçe cevap ver.")
            .put("input", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("reasoning", JSONObject().put("effort", "medium"))
        return extractOutputText(post(body))
    }

    fun reportAction(action: AgentAction, approved: Boolean, executed: Boolean, detail: String) {
        db.logAction(action, approved, executed, detail)
    }

    private fun post(body: JSONObject): JSONObject {
        val req = requestBuilder()
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) error(apiError(r.code, text))
            return JSONObject(text)
        }
    }

    private fun extractOutputText(root: JSONObject): String {
        root.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val output = root.optJSONArray("output") ?: error("Model yanıtı boş geldi")
        val pieces = mutableListOf<String>()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "output_text") {
                    part.optString("text").takeIf { it.isNotBlank() }?.let { pieces += it }
                }
            }
        }
        return pieces.joinToString("\n").trim().ifBlank { error("Model metin yanıtı üretmedi") }
    }

    private fun apiError(code: Int, body: String): String {
        val msg = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()
        return "OpenAI HTTP $code: ${msg?.takeIf { it.isNotBlank() } ?: body.take(500)}"
    }
}
