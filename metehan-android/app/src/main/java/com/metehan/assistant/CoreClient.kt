package com.metehan.assistant

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class CoreClient(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun builder(path: String): Request.Builder {
        val request = Request.Builder().url(CorePrefs.coreUrl(context) + path)
        val token = CorePrefs.accessToken(context)
        if (token.isNotBlank()) request.header("X-Metehan-Token", token)
        return request
    }

    private fun postJson(path: String, json: JSONObject): JSONObject {
        val body = json.toString().toRequestBody("application/json".toMediaType())
        client.newCall(builder(path).post(body).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("HTTP ${r.code}: $text")
            return JSONObject(text)
        }
    }

    fun health(): String {
        client.newCall(builder("/health").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return r.body?.string().orEmpty()
        }
    }

    fun command(message: String, deviceContext: JSONObject): AgentPlan {
        val response = postJson(
            "/api/agent-command",
            JSONObject().put("message", message).put("device_context", deviceContext),
        )
        val actionJson = response.optJSONObject("action") ?: JSONObject()
        val action = AgentAction(
            type = actionJson.optString("type", "none"),
            label = actionJson.optString("label", ""),
            target = actionJson.optString("target", ""),
        )
        return AgentPlan(
            reply = response.optString("reply", ""),
            confidence = response.optDouble("confidence", 0.5),
            needsConfirmation = response.optBoolean("needs_confirmation", action.type != "none"),
            action = action,
        )
    }

    fun reportAction(action: AgentAction, approved: Boolean, executed: Boolean, detail: String): Boolean {
        val response = postJson(
            "/api/device-action-result",
            JSONObject()
                .put("action_type", action.type)
                .put("label", action.label)
                .put("target", action.target)
                .put("approved", approved)
                .put("executed", executed)
                .put("detail", detail),
        )
        return response.optBoolean("ok", false)
    }

    fun analyzeImage(file: File, prompt: String): String {
        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val body = JSONObject()
            .put("image_base64", b64)
            .put("mime_type", "image/jpeg")
            .put("prompt", prompt)
            .toString()
            .toRequestBody("application/json".toMediaType())
        client.newCall(builder("/api/vision").post(body).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("HTTP ${r.code}: $text")
            return JSONObject(text).optString("analysis", text)
        }
    }
}
