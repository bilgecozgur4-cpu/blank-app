package com.metehan.assistant

import android.Manifest
import android.content.pm.PackageManager
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
import java.io.File
import java.util.concurrent.Executors

class CameraVisionActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView; private lateinit var result: TextView; private lateinit var prompt: EditText
    private var capture: ImageCapture? = null; private val cameraExecutor = Executors.newSingleThreadExecutor()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20,20,20,20)}
        previewView=PreviewView(this); box.addView(previewView,LinearLayout.LayoutParams(-1,0,1f))
        prompt=EditText(this).apply{setText("Bu görüntüde ne var? Gözlem ile çıkarımı ayır ve önemli ayrıntıları belirt.")}; box.addView(prompt)
        box.addView(Button(this).apply{text="METEHAN GÖR";setOnClickListener{takeAndAnalyze()}})
        result=TextView(this).apply{textSize=16f;setPadding(0,20,0,20)}; box.addView(result); setContentView(box)
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera() else {Toast.makeText(this,"Önce kamera izni ver",Toast.LENGTH_LONG).show();finish()}
    }
    private fun startCamera(){val future=ProcessCameraProvider.getInstance(this);future.addListener({val provider=future.get();val preview=Preview.Builder().build().also{it.setSurfaceProvider(previewView.surfaceProvider)};capture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();provider.unbindAll();provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,capture)},ContextCompat.getMainExecutor(this))}
    private fun takeAndAnalyze(){val imageCapture=capture?:return;val file=File.createTempFile("metehan_",".jpg",cacheDir);val options=ImageCapture.OutputFileOptions.Builder(file).build();result.text="Görüyorum…";imageCapture.takePicture(options,cameraExecutor,object:ImageCapture.OnImageSavedCallback{override fun onImageSaved(o:ImageCapture.OutputFileResults){try{val a=CoreClient(this@CameraVisionActivity).analyzeImage(file,prompt.text.toString());runOnUiThread{result.text=a}}catch(t:Throwable){runOnUiThread{result.text="Analiz hatası: ${t.message}"}}finally{file.delete()}};override fun onError(e:ImageCaptureException){runOnUiThread{result.text="Kamera hatası: ${e.message}"};file.delete()}})}
    override fun onDestroy(){cameraExecutor.shutdown();super.onDestroy()}
}
