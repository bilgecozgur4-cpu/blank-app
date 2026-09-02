package com.metehan.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

class MetehanVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onPrepareShow(args: Bundle?, showFlags: Int) { super.onPrepareShow(args, showFlags); setUiEnabled(false) }
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, MetehanPanelActivity::class.java).putExtra(MetehanPanelActivity.EXTRA_AUTOSTART, true).putExtra("wake_keyword", args?.getString("keyword") ?: "")
        startAssistantActivity(intent); hide()
    }
}
