package com.metehan.assistant

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.Locale

class CommandCenterActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var input: EditText
    private lateinit var resultText: TextView
    private lateinit var askButton: Button
    private lateinit var actionButton: Button
    private lateinit var speakButton: Button
    private var pendingPlan: AgentPlan? = null
    private var tts: TextToSpeech? = null

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) input.setText(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "METEHAN Komuta Merkezi"
        tts = TextToSpeech(this, this)

        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        scroll.addView(box)
        setContentView(scroll)

        box.addView(TextView(this).apply { text = "METEHAN"; textSize = 30f })
        box.addView(TextView(this).apply { text = "Native Komuta Merkezi · V0.5"; textSize = 16f })
        box.addView(TextView(this).apply {
            text = "Pil, şarj, ağ, saat dilimi ve cihaz durumunu bağlam olarak kullanır. Konumunu veya ekranını gizlice okumaz."
            setPadding(0, 14, 0, 18)
        })

        input = EditText(this).apply {
            hint = "Ne yapmamı veya ne düşünmemi istiyorsun?"
            minLines = 3
        }
        box.addView(input)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        askButton = Button(this).apply { text = "METEHAN'a sor"; setOnClickListener { submit() } }
        val micButton = Button(this).apply { text = "🎙 Söyle"; setOnClickListener { startSpeechInput() } }
        row.addView(askButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(micButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row)

        resultText = TextView(this).apply { textSize = 18f; setPadding(0, 28, 0, 18) }
        box.addView(resultText)

        speakButton = Button(this).apply {
            text = "Cevabı seslendir"
            visibility = View.GONE
            setOnClickListener { speak(resultText.text.toString()) }
        }
        box.addView(speakButton)

        actionButton = Button(this).apply {
            visibility = View.GONE
            setOnClickListener { confirmAndExecute() }
        }
        box.addView(actionButton)

        intent.getStringExtra("prefill")?.takeIf { it.isNotBlank() }?.let { input.setText(it) }
    }

    private fun submit() {
        val message = input.text.toString().trim()
        if (message.isBlank()) return
        askButton.isEnabled = false
        actionButton.visibility = View.GONE
        speakButton.visibility = View.GONE
        pendingPlan = null
        resultText.text = "METEHAN düşünüyor…"
        Thread {
            val response = runCatching {
                CoreClient(this).command(message, DeviceContextCollector.collect(this))
            }
            runOnUiThread {
                askButton.isEnabled = true
                response.onSuccess { plan -> renderPlan(plan) }
                    .onFailure { resultText.text = "Bağlantı hatası: ${it.message}" }
            }
        }.start()
    }

    private fun renderPlan(plan: AgentPlan) {
        pendingPlan = plan
        val confidence = (plan.confidence * 100).toInt().coerceIn(0, 100)
        resultText.text = "${plan.reply}\n\nGüven: %$confidence"
        speakButton.visibility = View.VISIBLE
        if (plan.action.type != "none") {
            actionButton.text = "Önerilen eylem · ${plan.action.label.ifBlank { plan.action.type }}"
            actionButton.visibility = View.VISIBLE
            actionButton.isEnabled = true
        } else {
            actionButton.visibility = View.GONE
        }
    }

    private fun confirmAndExecute() {
        val plan = pendingPlan ?: return
        val action = plan.action
        val targetLine = action.target.takeIf { it.isNotBlank() }?.let { "\n\nHedef: $it" }.orEmpty()
        AlertDialog.Builder(this)
            .setTitle("Eylemi onayla")
            .setMessage("${action.label.ifBlank { action.type }}$targetLine\n\nMETEHAN bunu ancak sen onaylarsan çalıştıracak.")
            .setNegativeButton("Vazgeç") { _, _ -> reportAction(action, approved = false, executed = false, detail = "user_cancelled") }
            .setPositiveButton("Onayla") { _, _ ->
                actionButton.isEnabled = false
                val execution = ActionDispatcher.execute(this, action)
                execution.onSuccess {
                    Toast.makeText(this, "Eylem açıldı", Toast.LENGTH_SHORT).show()
                    reportAction(action, approved = true, executed = true, detail = "ok")
                }.onFailure {
                    Toast.makeText(this, "Eylem hatası: ${it.message}", Toast.LENGTH_LONG).show()
                    reportAction(action, approved = true, executed = false, detail = it.message ?: "error")
                    actionButton.isEnabled = true
                }
            }
            .show()
    }

    private fun reportAction(action: AgentAction, approved: Boolean, executed: Boolean, detail: String) {
        Thread { runCatching { CoreClient(this).reportAction(action, approved, executed, detail) } }.start()
    }

    private fun startSpeechInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "METEHAN için komutunu söyle")
        }
        runCatching { speechLauncher.launch(intent) }
            .onFailure { Toast.makeText(this, "Konuşma tanıma kullanılamıyor", Toast.LENGTH_SHORT).show() }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale("tr", "TR")
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "metehan_reply")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
