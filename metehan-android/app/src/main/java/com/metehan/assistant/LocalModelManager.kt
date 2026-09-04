package com.metehan.assistant

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object LocalModelManager {
    const val MODEL_FILE_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    const val MODEL_LABEL = "Qwen2.5 0.5B · Q4_K_M"
    const val APPROX_SIZE_MB = 491
    private const val MIN_VALID_BYTES = 400_000_000L
    private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true"

    fun modelFile(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILE_NAME)
    }

    fun isReady(context: Context): Boolean {
        val file = modelFile(context)
        return file.exists() && file.length() >= MIN_VALID_BYTES
    }

    fun sizeMb(context: Context): Long = modelFile(context).takeIf { it.exists() }?.length()?.div(1024L * 1024L) ?: 0L

    fun delete(context: Context): Boolean {
        val file = modelFile(context)
        val part = File(file.parentFile, "$MODEL_FILE_NAME.part")
        part.delete()
        return !file.exists() || file.delete()
    }

    fun download(
        context: Context,
        onProgress: (Int, Long, Long) -> Unit,
        onComplete: (Result<File>) -> Unit,
    ) {
        Thread {
            val result = runCatching {
                val target = modelFile(context)
                if (isReady(context)) return@runCatching target
                val part = File(target.parentFile, "$MODEL_FILE_NAME.part")
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.MINUTES)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                val req = Request.Builder().url(MODEL_URL).header("User-Agent", "METEHAN-Android/0.7").build()
                client.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) error("Model indirme HTTP ${response.code}")
                    val body = response.body ?: error("Model indirme yanıtı boş")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(part).use { output ->
                            val buffer = ByteArray(1024 * 256)
                            var downloaded = 0L
                            var lastPercent = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val percent = if (total > 0) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent, downloaded, total)
                                }
                            }
                            output.flush()
                        }
                    }
                }
                if (part.length() < MIN_VALID_BYTES) error("Model dosyası eksik indi (${part.length() / 1024 / 1024} MB)")
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                target
            }
            onComplete(result)
        }.start()
    }
}
