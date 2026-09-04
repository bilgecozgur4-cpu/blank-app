package com.metehan.assistant

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class CommandCenterActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var input: EditText
    private lateinit var resultText: TextView
    private lateinit var askButton: Button
    private lateinit var actionButton: Button
    private lateinit var speakButton: Button
    private var pendingPlan: AgentPlan? = null
    private var tts: TextToSpeech? = null
    private var autoSubmitAfterSpeech = false
    private var autoSpeakReply = false

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                input.setText(text)
                if (autoSubmitAfterSpeech) {
                    autoSubmitAfterSpeech = false
                    submit()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "METEHAN Komuta Merkezi"
        tts = TextToSpeech(this, this)
        autoSpeakReply = intent.getBooleanExtra("auto_speak", false)

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(5, 8, 13)) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(38))
            setBackgroundColor(Color.rgb(5, 8, 13))
        }
        scroll.addView(box)
        setContentView(scroll)

        box.addView(ImageView(this).apply {
            setImageResource(R.drawable.metehan_icon)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(145))
        })
        box.addView(TextView(this).apply {
            text = "METEHAN"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(237, 244, 255))
        })
        box.addView(TextView(this).apply {
            text = "Ücretsiz Yerel Komuta Merkezi · V0.7"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(125, 211, 252))
        })
        box.addView(TextView(this).apply {
            text = "API yok · token ücreti yok. Telefon komutları model olmadan bile çalışır; genel sohbet yerel GGUF modelini kullanır."
            setTextColor(Color.rgb(167, 243, 208))
            setPadding(0, dp(14), 0, dp(18))
        })

        input = EditText(this).apply {
            hint = "Ne yapmamı veya ne düşünmemi istiyorsun?"
            minLines = 3
            setTextColor(Color.rgb(237, 244, 255))
            setHintTextColor(Color.rgb(137, 152, 170))
            setBackgroundColor(Color.rgb(10, 16, 24))
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        box.addView(input)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        askButton = styledButton("METEHAN'a sor") { submit() }
        val micButton = styledButton("🎙 Söyle") { startSpeechInput(autoSubmit = false) }
        row.addView(askButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(micButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row)

        box.addView(styledButton("🧠 Bunu hafızaya al") {
            val text = input.text.toString().trim()
            if (text.isBlank()) Toast.makeText(this, "Önce hatırlanacak şeyi yaz", Toast.LENGTH_SHORT).show()
            else {
                MetehanLocalDb(this).remember(text)
                Toast.makeText(this, "METEHAN hafızasına kaydedildi", Toast.LENGTH_SHORT).show()
            }
        })

        resultText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.rgb(237, 244, 255))
            setPadding(0, dp(28), 0, dp(18))
        }
        box.addView(resultText)

        speakButton = styledButton("Cevabı seslendir") { speak(resultText.text.toString()) }.apply { visibility = View.GONE }
        box.addView(speakButton)

        actionButton = styledButton("Eylem") { confirmAndExecute() }.apply { visibility = View.GONE }
        box.addView(actionButton)

        intent.getStringExtra("prefill")?.takeIf { it.isNotBlank() }?.let { input.setText(it) }
        if (intent.getBooleanExtra("auto_speech", false)) {
            autoSpeakReply = true
            scroll.postDelayed({ startSpeechInput(autoSubmit = true) }, 450)
        }
    }

    private fun submit() {
        val message = input.text.toString().trim()
        if (message.isBlank()) return

        if (message.lowercase(Locale("tr", "TR")).startsWith("hatırla ")) {
            val memory = message.substringAfter(' ').trim()
            MetehanLocalDb(this).remember(memory)
            resultText.text = "Kaydettim. Bunu yerel hafızamda tutacağım."
            speakButton.visibility = View.VISIBLE
            if (autoSpeakReply) speak(resultText.text.toString())
            return
        }

        askButton.isEnabled = false
        actionButton.visibility = View.GONE
        speakButton.visibility = View.GONE
        pendingPlan = null
        resultText.text = if (LocalModelManager.isReady(this)) "METEHAN yerel modelde düşünüyor…" else "METEHAN yerel komut motorunu kontrol ediyor…"

        lifecycleScope.launch {
            val response = runCatching {
                withContext(Dispatchers.IO) {
                    OfflineLlmClient(this@CommandCenterActivity).command(message, DeviceContextCollector.collect(this@CommandCenterActivity))
                }
            }
            askButton.isEnabled = true
            response.onSuccess { plan -> renderPlan(plan) }
                .onFailure {
                    resultText.text = "METEHAN yerel motor hatası: ${it.message}\n\nAna ekrandaki SİSTEM TESTİ'ni çalıştır."
                    speakButton.visibility = View.VISIBLE
                }
        }
    }

    private fun renderPlan(plan: AgentPlan) {
        pendingPlan = plan
        val confidence = (plan.confidence * 100).toInt().coerceIn(0, 100)
        resultText.text = "${plan.reply}\n\nGüven: %$confidence"
        speakButton.visibility = View.VISIBLE
        if (autoSpeakReply) speak(plan.reply)
        if (plan.action.type != "none") {
            actionButton.text = "Önerilen eylem · ${plan.action.label.ifBlank { plan.action.type }}"
            actionButton.visibility = View.VISIBLE
            actionButton.isEnabled = true
        } else actionButton.visibility = View.GONE
    }

    private fun confirmAndExecute() {
        val plan = pendingPlan ?: return
        val action = plan.action
        val targetLine = action.target.takeIf { it.isNotBlank() }?.let { "\n\nHedef: $it" }.orEmpty()
        AlertDialog.Builder(this)
            .setTitle("Eylemi onayla")
            .setMessage("${action.label.ifBlank { action.type }}$targetLine\n\nMETEHAN bunu ancak sen onaylarsan çalıştıracak.")
            .setNegativeButton("Vazgeç") { _, _ -> reportAction(action, false, false, "user_cancelled") }
            .setPositiveButton("Onayla") { _, _ ->
                actionButton.isEnabled = false
                val execution = ActionDispatcher.execute(this, action)
                execution.onSuccess {
                    Toast.makeText(this, "Eylem açıldı", Toast.LENGTH_SHORT).show()
                    reportAction(action, true, true, "ok")
                }.onFailure {
                    Toast.makeText(this, "Eylem hatası: ${it.message}", Toast.LENGTH_LONG).show()
                    reportAction(action, true, false, it.message ?: "error")
                    actionButton.isEnabled = true
                }
            }.show()
    }

    private fun reportAction(action: AgentAction, approved: Boolean, executed: Boolean, detail: String) {
        OfflineLlmClient(this).reportAction(action, approved, executed, detail)
    }

    private fun startSpeechInput(autoSubmit: Boolean) {
        autoSubmitAfterSpeech = autoSubmit
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "METEHAN için komutunu söyle")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        runCatching { speechLauncher.launch(speechIntent) }
            .onFailure { Toast.makeText(this, "Konuşma tanıma kullanılamıyor", Toast.LENGTH_SHORT).show() }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("tr", "TR")
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "metehan_reply")
    }

    private fun styledButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(Color.rgb(237, 244, 255))
        setBackgroundColor(Color.rgb(18, 31, 45))
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
