package com.metehan.assistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private var testWake: WakeWordController? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { updateStatus() }
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); title = "METEHAN"
        val outer = ScrollView(this); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40,40,40,40) }; outer.addView(box); setContentView(outer)
        box.addView(TextView(this).apply { text = "METEHAN"; textSize = 32f })
        box.addView(TextView(this).apply { text = "Kişisel Yapay Zekâ Başdanışman · Native V0.4"; textSize = 16f })
        status = TextView(this).apply { textSize = 17f; setPadding(0,28,0,28) }; box.addView(status)
        box.addView(button("1 · Mikrofon ve kamera izinleri") { permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)) })
        box.addView(button("2 · Metehan'ı varsayılan asistan yap") { requestAssistantRole() })
        box.addView(button("Wake-word testi (ekran açıkken)") { toggleWakeTest() })
        box.addView(button("Canlı Metehan panelini aç") { startActivity(Intent(this, MetehanPanelActivity::class.java)) })
        box.addView(button("Kamera · Metehan gör") { startActivity(Intent(this, CameraVisionActivity::class.java)) })
        box.addView(TextView(this).apply { text = "\nÇekirdek bağlantısı"; textSize = 20f })
        val url = TextInputEditText(this).apply { setText(CorePrefs.coreUrl(this@MainActivity)); hint = "https://... veya http://127.0.0.1:8765" }
        val token = TextInputEditText(this).apply { setText(CorePrefs.accessToken(this@MainActivity)); hint = "Erişim anahtarı (opsiyonel)" }
        box.addView(url); box.addView(token)
        box.addView(button("Bağlantıyı kaydet ve test et") {
            try {
                CorePrefs.save(this, url.text.toString(), token.text.toString())
                Thread { val msg = runCatching { CoreClient(this).health() }.fold({ "Çekirdek hazır: $it" }, { "Bağlantı hatası: ${it.message}" }); runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); updateStatus() } }.start()
            } catch (t: Throwable) { Toast.makeText(this, t.message, Toast.LENGTH_LONG).show() }
        })
        box.addView(button("Android ses giriş ayarlarını aç") { runCatching { startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) } })
        updateStatus()
    }
    private fun button(text: String, click: () -> Unit) = Button(this).apply { this.text=text; setOnClickListener { click() } }
    private fun requestAssistantRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)) roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)) else updateStatus()
    }
    private fun toggleWakeTest() {
        if (testWake?.isRunning() == true) { testWake?.stop(); testWake=null; Toast.makeText(this,"Wake-word testi kapandı",Toast.LENGTH_SHORT).show(); return }
        testWake = WakeWordController(this, { Toast.makeText(this,"Uyandı: $it",Toast.LENGTH_LONG).show() }, { Toast.makeText(this,it,Toast.LENGTH_LONG).show() }); testWake?.start()
    }
    private fun updateStatus() {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val role = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
        status.text = "Mikrofon: ${if(mic)"✓" else "—"}   Kamera: ${if(cam)"✓" else "—"}\nVarsayılan asistan: ${if(role)"METEHAN ✓" else "henüz değil"}\nÇekirdek: ${CorePrefs.coreUrl(this)}"
    }
    override fun onDestroy() { testWake?.stop(); super.onDestroy() }
}
