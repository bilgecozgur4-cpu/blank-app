package com.metehan.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

class MetehanRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent, listener: Callback) { listener.error(SpeechRecognizer.ERROR_CLIENT) }
    override fun onCancel(listener: Callback) = Unit
    override fun onStopListening(listener: Callback) = Unit
}
