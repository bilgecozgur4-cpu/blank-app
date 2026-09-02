package com.metehan.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.util.Log
import androidx.core.content.ContextCompat

class MetehanVoiceInteractionService : VoiceInteractionService() {
    private var wakeWord: WakeWordController? = null
    override fun onReady() {
        super.onReady()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { Log.w("Metehan", "RECORD_AUDIO izni yok; wake word devre dışı"); return }
        wakeWord?.stop()
        wakeWord = WakeWordController(this, { keyword ->
            val args = Bundle().apply { putBoolean("metehan_wake", true); putString("keyword", keyword) }
            showSession(args, VoiceInteractionSession.SHOW_WITH_ASSIST)
        }, { Log.e("Metehan", it) })
        wakeWord?.start()
    }
    override fun onShutdown() { wakeWord?.stop(); wakeWord = null; super.onShutdown() }
}
