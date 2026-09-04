package com.metehan.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

class CameraVisionActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var result: TextView
    private lateinit var prompt: EditText
    private var capture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.rgb(5, 8, 13))
        }
        previewView = PreviewView(this)
        box.addView(previewView, LinearLayout.LayoutParams(-1, 0, 1f))
        prompt = EditText(this).apply {
            setText("Bu görüntüde ne var? Önemli nesneleri ve sahneyi kısa anlat.")
            setTextColor(Color.rgb(237, 244, 255))
            setHintTextColor(Color.rgb(137, 152, 170))
        }
        box.addView(prompt)
        box.addView(Button(this).apply {
            text = "METEHAN GÖR · ÜCRETSİZ"
            setOnClickListener { takeAndAnalyze() }
        })
        result = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(237, 244, 255))
            setPadding(0, 20, 0, 20)
        }
        box.addView(result)
        setContentView(box)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else {
            Toast.makeText(this, "Önce kamera izni ver", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takeAndAnalyze() {
        val imageCapture = capture ?: return
        val file = File.createTempFile("metehan_", ".jpg", cacheDir)
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        result.text = "METEHAN görüntüyü cihaz içinde analiz ediyor…"
        imageCapture.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                runOnUiThread { runLocalLabeling(file) }
            }

            override fun onError(e: ImageCaptureException) {
                runOnUiThread { result.text = "Kamera hatası: ${e.message}" }
                file.delete()
            }
        })
    }

    private fun runLocalLabeling(file: File) {
        val inputImage = runCatching { InputImage.fromFilePath(this, Uri.fromFile(file)) }.getOrElse {
            result.text = "Görüntü hazırlama hatası: ${it.message}"
            file.delete()
            return
        }
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.55f)
                .build(),
        )
        labeler.process(inputImage)
            .addOnSuccessListener { labels ->
                val top = labels.sortedByDescending { it.confidence }.take(8)
                val raw = if (top.isEmpty()) {
                    "Belirgin nesne etiketi bulamadım."
                } else {
                    top.joinToString("\n") { "• ${it.text} · %${(it.confidence * 100).toInt()}" }
                }
                if (LocalModelManager.isReady(this)) {
                    result.text = "Yerel görüntü etiketleri:\n$raw\n\nMETEHAN yorumluyor…"
                    lifecycleScope.launch {
                        val promptText = buildString {
                            append("Kameranın cihaz içi görüntü etiketleri şunlar:\n").append(raw)
                            append("\nKullanıcının isteği: ").append(prompt.text.toString())
                            append("\nSadece bu etiketlerden güvenle çıkarılabilecek şeyleri Türkçe ve kısa anlat; görmediğin ayrıntıyı uydurma.")
                        }
                        val plan = runCatching {
                            withContext(Dispatchers.IO) {
                                OfflineLlmClient(this@CameraVisionActivity).command(promptText, DeviceContextCollector.collect(this@CameraVisionActivity))
                            }
                        }
                        result.text = plan.fold(
                            onSuccess = { "Yerel görüntü etiketleri:\n$raw\n\nMETEHAN:\n${it.reply}" },
                            onFailure = { "Yerel görüntü etiketleri:\n$raw\n\nYorum motoru hatası: ${it.message}" },
                        )
                    }
                } else {
                    result.text = "Yerel görüntü etiketleri:\n$raw\n\nDaha ayrıntılı Türkçe yorum için ana ekrandan ücretsiz yerel AI modelini indir."
                }
            }
            .addOnFailureListener { result.text = "Yerel görüntü analizi hatası: ${it.message}" }
            .addOnCompleteListener {
                labeler.close()
                file.delete()
            }
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
