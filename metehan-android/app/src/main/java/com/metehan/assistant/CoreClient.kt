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
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build()

    private fun builder(path: String): Request.Builder {
        val request = Request.Builder().url(CorePrefs.coreUrl(context) + path)
        val token = CorePrefs.accessToken(context)
        if (token.isNotBlank()) request.header("X-Metehan-Token", token)
        return request
    }

    fun health(): String {
        client.newCall(builder("/health").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return r.body?.string().orEmpty()
        }
    }

    fun analyzeImage(file: File, prompt: String): String {
        val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        val body = JSONObject().put("image_base64", b64).put("mime_type", "image/jpeg").put("prompt", prompt).toString().toRequestBody("application/json".toMediaType())
        client.newCall(builder("/api/vision").post(body).build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) error("HTTP ${r.code}: $text")
            return JSONObject(text).optString("analysis", text)
        }
    }
}
