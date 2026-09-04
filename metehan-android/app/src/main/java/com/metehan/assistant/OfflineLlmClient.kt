package com.metehan.assistant

import android.content.Context
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import org.json.JSONObject

class OfflineLlmClient(private val context: Context) {
    private val db = MetehanLocalDb(context)

    suspend fun command(message: String, deviceContext: JSONObject): AgentPlan {
        LocalActionRouter.route(message, deviceContext)?.let { return it }
        val modelFile = LocalModelManager.modelFile(context)
        if (!LocalModelManager.isReady(context)) {
            return AgentPlan(
                reply = "Ücretsiz yerel AI modeli henüz indirilmemiş. Ana ekrandan yaklaşık ${LocalModelManager.APPROX_SIZE_MB} MB'lık modeli bir kez indir. Telefon komutlarım model olmadan da çalışır.",
                confidence = 1.0,
                needsConfirmation = false,
                action = AgentAction("none", "", ""),
            )
        }

        val memories = db.relevantMemories(message, 8)
        val recent = db.recentChat(8)
        val prompt = buildString {
            append("Kullanıcının yeni mesajı: ").append(message).append("\n\n")
            if (memories.isNotEmpty()) {
                append("İlgili kişisel hafıza:\n")
                memories.forEach { append("- ").append(it).append('\n') }
                append('\n')
            }
            if (recent.isNotEmpty()) {
                append("Yakın konuşma geçmişi:\n")
                recent.forEach { (role, text) ->
                    append(if (role == "assistant") "METEHAN: " else "Kullanıcı: ").append(text.take(1200)).append('\n')
                }
                append('\n')
            }
            append("Telefon bağlamı: ").append(deviceContext.toString()).append('\n')
            append("Şimdi kullanıcının mesajını yanıtla.")
        }

        val system = """
            Sen METEHAN'sın; cihaz içinde çalışan kişisel yapay zekâ başdanışmanısın.
            Türkçe konuş. Net, faydalı ve gerektiğinde eleştirel ol. Kullanıcıya otomatik katılma.
            Bilmediğin şeyi uydurma. Önemli konularda gözlem, çıkarım ve belirsizliği ayır.
            Bu cihaz içi sürüm internette güncel araştırma yapamaz; güncel veri gerektiren yerde bunu açıkça söyle.
            Cevabı gereksiz uzatma. Kullanıcının kişisel hafızasını yalnızca verilen hafıza notları kadar kullan.
        """.trimIndent()

        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
        val model = Llama.loadModel(
            modelPath = modelFile.absolutePath,
            config = LlamaConfig(contextSize = 2048, threads = threads),
        )
        return try {
            val result = Llama.complete(
                model,
                prompt = prompt,
                systemPrompt = system,
                maxTokens = 320,
            )
            val reply = result.text.trim().ifBlank { "Yanıt üretemedim; soruyu farklı biçimde sor." }
            db.addChat("user", message)
            db.addChat("assistant", reply)
            AgentPlan(reply, 0.72, false, AgentAction("none", "", ""))
        } finally {
            Llama.releaseModel(model)
        }
    }

    fun reportAction(action: AgentAction, approved: Boolean, executed: Boolean, detail: String) {
        db.logAction(action, approved, executed, detail)
    }
}
