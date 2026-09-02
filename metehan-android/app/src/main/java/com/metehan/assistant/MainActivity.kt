package com.metehan.assistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private var testWake: WakeWordController? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { updateStatus() }
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "METEHAN"

        val outer = ScrollView(this).apply { setBackgroundColor(Color.rgb(5, 8, 13)) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(36))
            setBackgroundColor(Color.rgb(5, 8, 13))
        }
        outer.addView(box)
        setContentView(outer)

        box.addView(ImageView(this).apply {
            setImageResource(R.drawable.metehan_splash)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(300))
            contentDescription = "METEHAN"
        })

        box.addView(TextView(this).apply {
            text = "Kişisel Yapay Zekâ Başdanışman · Standalone V0.6"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(125, 211, 252))
            setPadding(0, 0, 0, dp(12))
        })

        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(237, 244, 255))
            setPadding(0, dp(8), 0, dp(18))
        }
        box.addView(status)

        box.addView(button("⚡ METEHAN KOMUTA MERKEZİ") {
            startActivity(Intent(this, CommandCenterActivity::class.java))
        })
        box.addView(button("1 · MİKROFON VE KAMERA İZİNLERİ") {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
        })
        box.addView(button("2 · METEHAN'I VARSAYILAN ASİSTAN YAP") { requestAssistantRole() })
        box.addView(button("WAKE-WORD TESTİ · 'METEHAN'") { toggleWakeTest() })
        box.addView(button("KAMERA · METEHAN GÖR") {
            startActivity(Intent(this, CameraVisionActivity::class.java))
        })

        box.addView(TextView(this).apply {
            text = "\nAI motoru"
            textSize = 22f
            setTextColor(Color.rgb(237, 244, 255))
        })
        box.addView(TextView(this).apply {
            text = "AI doğrudan uygulamanın içinde çalışır. API anahtarı Android Keystore ile cihazda şifrelenir."
            textSize = 14f
            setTextColor(Color.rgb(137, 152, 170))
            setPadding(0, dp(6), 0, dp(10))
        })

        val apiKey = TextInputEditText(this).apply {
            hint = "OpenAI API anahtarı"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.rgb(237, 244, 255))
            setHintTextColor(Color.rgb(137, 152, 170))
        }
        val model = TextInputEditText(this).apply {
            hint = "Model"
            setText(SecurePrefs.model(this@MainActivity))
            setTextColor(Color.rgb(237, 244, 255))
            setHintTextColor(Color.rgb(137, 152, 170))
        }
        box.addView(apiKey)
        box.addView(model)

        box.addView(button("API AYARINI KAYDET VE TEST ET") {
            val entered = apiKey.text?.toString()?.trim().orEmpty()
            if (entered.isNotBlank()) SecurePrefs.saveApiKey(this, entered)
            SecurePrefs.saveModel(this, model.text?.toString().orEmpty())
            apiKey.setText("")

            if (!SecurePrefs.hasApiKey(this)) {
                Toast.makeText(this, "Önce API anahtarını gir", Toast.LENGTH_LONG).show()
                updateStatus()
            } else {
                Thread {
                    val msg = runCatching { StandaloneAiClient(this).testConnection() }
                        .fold({ it }, { "API hatası: ${it.message}" })
                    runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        updateStatus()
                    }
                }.start()
            }
        })

        box.addView(button("YEREL SOHBET GEÇMİŞİNİ TEMİZLE") {
            AlertDialog.Builder(this)
                .setTitle("Sohbet geçmişini temizle")
                .setMessage("METEHAN'ın cihazdaki sohbet geçmişi silinecek. Kalıcı hafıza notları silinmez.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Temizle") { _, _ ->
                    MetehanLocalDb(this).clearConversation()
                    Toast.makeText(this, "Yerel sohbet geçmişi temizlendi", Toast.LENGTH_SHORT).show()
                }.show()
        })

        box.addView(button("ANDROID SES GİRİŞ AYARLARINI AÇ") {
            runCatching { startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
        })
        updateStatus()
    }

    private fun button(text: String, click: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.rgb(237, 244, 255))
        setBackgroundColor(Color.rgb(18, 31, 45))
        setPadding(dp(10), dp(14), dp(10), dp(14))
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        p.setMargins(0, dp(5), 0, dp(5))
        layoutParams = p
        setOnClickListener { click() }
    }

    private fun requestAssistantRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
        } else updateStatus()
    }

    private fun toggleWakeTest() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Önce mikrofon izni ver", Toast.LENGTH_LONG).show()
            return
        }
        if (testWake?.isRunning() == true) {
            testWake?.stop()
            testWake = null
            Toast.makeText(this, "Wake-word testi kapandı", Toast.LENGTH_SHORT).show()
            return
        }
        testWake = WakeWordController(
            this,
            { Toast.makeText(this, "METEHAN uyandı ✓", Toast.LENGTH_LONG).show() },
            { Toast.makeText(this, it, Toast.LENGTH_LONG).show() },
        )
        testWake?.start()
    }

    private fun updateStatus() {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val role = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
        status.text = "Standalone beyin: HAZIR ✓\n" +
            "AI anahtarı: ${if (SecurePrefs.hasApiKey(this)) "AYARLI ✓" else "EKSİK"}\n" +
            "Model: ${SecurePrefs.model(this)}\n" +
            "Mikrofon: ${if (mic) "✓" else "—"}   Kamera: ${if (cam) "✓" else "—"}\n" +
            "Varsayılan asistan: ${if (role) "METEHAN ✓" else "henüz değil"}"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
    }

    override fun onDestroy() {
        testWake?.stop()
        super.onDestroy()
    }
}
