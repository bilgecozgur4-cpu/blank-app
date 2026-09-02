package com.metehan.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getKwsModelConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max

class WakeWordController(private val context: Context, private val onDetected: (String) -> Unit, private val onError: (String) -> Unit = {}) {
    companion object { private const val SAMPLE_RATE = 16000; private const val MODEL_DIR = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01" }
    private val running = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private var kws: KeywordSpotter? = null
    private var stream: OnlineStream? = null

    fun start(): Boolean {
        if (running.get()) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { onError("Mikrofon izni yok"); return false }
        return try {
            val config = KeywordSpotterConfig(
                featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = getKwsModelConfig(type = 1)!!,
                keywordsFile = "$MODEL_DIR/keywords.txt",
                keywordsScore = 2.2f,
                keywordsThreshold = 0.35f,
                numTrailingBlanks = 2,
            )
            kws = KeywordSpotter(assetManager = context.assets, config = config)
            stream = kws!!.createStream()
            val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufferSize = max(min, 4096) * 2
            audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) error("AudioRecord başlatılamadı")
            running.set(true); audioRecord!!.startRecording()
            worker = thread(name = "metehan-wake-word", isDaemon = true) { decodeLoop(bufferSize / 2) }
            true
        } catch (t: Throwable) { stop(); main.post { onError("Wake-word başlatılamadı: ${t.message}") }; false }
    }

    private fun decodeLoop(shortCount: Int) {
        val buffer = ShortArray(shortCount)
        try {
            while (running.get()) {
                val n = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (n <= 0) continue
                val samples = FloatArray(n) { i -> buffer[i] / 32768.0f }
                val localStream = stream ?: break
                val localKws = kws ?: break
                localStream.acceptWaveform(samples, sampleRate = SAMPLE_RATE)
                while (localKws.isReady(localStream)) {
                    localKws.decode(localStream)
                    val keyword = localKws.getResult(localStream).keyword
                    if (keyword.isNotBlank()) { localKws.reset(localStream); main.post { onDetected(keyword) } }
                }
            }
        } catch (t: Throwable) { if (running.get()) main.post { onError("Wake-word hatası: ${t.message}") } }
    }

    fun stop() {
        running.set(false)
        runCatching { audioRecord?.stop() }; runCatching { audioRecord?.release() }; audioRecord = null
        runCatching { stream?.release() }; stream = null
        runCatching { kws?.release() }; kws = null; worker = null
    }
    fun isRunning(): Boolean = running.get()
}
