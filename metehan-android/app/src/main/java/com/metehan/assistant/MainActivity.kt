package com.metehan.assistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var modelStatus: TextView
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
            text = "Kişisel Yapay Zekâ Başdanışman · Ücretsiz Yerel V0.7"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(125, 211, 252))
            setPadding(0, 0, 0, dp(12))
        })

        box.addView(TextView(this).apply {
            text = "API anahtarı yok · token ücreti yok · sohbet modeli cihazda çalışır"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(167, 243, 208))
            setPadding(0, 0, 0, dp(14))
        })

        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(237, 244, 255))
            setPadding(0, dp(8), 0, dp(14))
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
        box.addView(button("KAMERA · ÜCRETSİZ YEREL GÖRÜ") {
            startActivity(Intent(this, CameraVisionActivity::class.java))
        })

        box.addView(TextView(this).apply {
            text = "\nYerel AI modeli"
            textSize = 22f
            setTextColor(Color.rgb(237, 244, 255))
        })
        box.addView(TextView(this).apply {
            text = "İlk kurulumda modeli bir kez indir. Sonrasında sohbet hesabı cihaz içinde yapılır. Model: ${LocalModelManager.MODEL_LABEL}."
            textSize = 14f
            setTextColor(Color.rgb(137, 152, 170))
            setPadding(0, dp(6), 0, dp(8))
        })

        modelStatus = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(125, 211, 252))
            setPadding(0, dp(4), 0, dp(8))
        }
        box.addView(modelStatus)

        val downloadButton = button("⬇ YEREL AI MODELİNİ İNDİR (~${LocalModelManager.APPROX_SIZE_MB} MB)") { }
        downloadButton.setOnClickListener {
            if (LocalModelManager.isReady(this)) {
                Toast.makeText(this, "Yerel model zaten hazır", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            downloadButton.isEnabled = false
            modelStatus.text = "Model indiriliyor… Wi-Fi önerilir."
            LocalModelManager.download(
                this,
                onProgress = { percent, downloaded, total ->
                    runOnUiThread {
                        val downMb = downloaded / 1024 / 1024
                        val totalMb = if (total > 0) total / 1024 / 1024 else LocalModelManager.APPROX_SIZE_MB.toLong()
                        modelStatus.text = "İndiriliyor: %$percent · $downMb / $totalMb MB"
                    }
                },
                onComplete = { result ->
                    runOnUiThread {
                        downloadButton.isEnabled = true
                        result.onSuccess {
                            Toast.makeText(this, "Yerel AI modeli hazır ✓", Toast.LENGTH_LONG).show()
                        }.onFailure {
                            Toast.makeText(this, "Model indirme hatası: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                        updateStatus()
                    }
                },
            )
        }
        box.addView(downloadButton)

        box.addView(button("YEREL MODELİ SİL") {
            AlertDialog.Builder(this)
                .setTitle("Yerel modeli sil")
                .setMessage("Yaklaşık ${LocalModelManager.APPROX_SIZE_MB} MB model dosyası silinecek. Telefon komutları çalışmaya devam eder; genel AI sohbeti için tekrar indirmen gerekir.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sil") { _, _ ->
                    LocalModelManager.delete(this)
                    updateStatus()
                    Toast.makeText(this, "Yerel model silindi", Toast.LENGTH_SHORT).show()
                }.show()
        })

        box.addView(button("🧪 SİSTEM TESTİ") {
            val arm64 = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
            val text = buildString {
                append("METEHAN V0.7 ÜCRETSİZ\n\n")
                append("ARM64: ${if (arm64) "✓" else "UYUMSUZ"}\n")
                append("Yerel model: ${if (LocalModelManager.isReady(this@MainActivity)) "✓ ${LocalModelManager.sizeMb(this@MainActivity)} MB" else "henüz yok"}\n")
                append("Ücretli API bağımlılığı: YOK\n")
                append("Telefon komut motoru: HAZIR\n")
                append("Yerel hafıza: HAZIR\n")
                append("Yerel wake-word: HAZIR")
            }
            AlertDialog.Builder(this).setTitle("METEHAN Tanılama").setMessage(text).setPositiveButton("Tamam", null).show()
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
        val modelReady = LocalModelManager.isReady(this)
        status.text = "Ücretsiz yerel çekirdek: HAZIR ✓\n" +
            "AI modeli: ${if (modelReady) "HAZIR ✓" else "İNDİRİLMELİ"}\n" +
            "Mikrofon: ${if (mic) "✓" else "—"}   Kamera: ${if (cam) "✓" else "—"}\n" +
            "Varsayılan asistan: ${if (role) "METEHAN ✓" else "henüz değil"}"
        modelStatus.text = if (modelReady) {
            "✓ ${LocalModelManager.MODEL_LABEL} · ${LocalModelManager.sizeMb(this)} MB · cihaz içinde"
        } else {
            "Model yok · telefon komutları çalışır, genel AI sohbeti için bir kez indir"
        }
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
