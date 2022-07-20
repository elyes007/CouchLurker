package com.elyes.couchlurker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.LENS_FACING_FRONT
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.elyes.couchlurker.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding

    private val faceDetector = FaceDetection.getClient()
    private val imageAnalyzer = ImageAnalysis.Analyzer { imageProxy ->
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            faceDetector.process(image)
                .addOnSuccessListener {
                    Log.d(this.localClassName, "number of faces: ${it.size}")
                    // TODO success handler
                }
                .addOnFailureListener {
                    // TODO failure handler
                }
        }
        imageProxy.close()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        handlePermissions()
    }

    private fun handlePermissions() {
        val isPermissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        if (isPermissionGranted) {
            prepareCameraCapture()
        } else {
            val requestPermissionLauncher =
                registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    if (isGranted) {
                        prepareCameraCapture()
                    } else {
                        finish()
                    }
                }
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun prepareCameraCapture() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val imageCapture = ImageCapture.Builder().build()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(LENS_FACING_FRONT)
                    .build()

                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    imageCapture
                )
                observeDetectionRequests(imageCapture)
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun observeDetectionRequests(imageCapture: ImageCapture) {
        Observable.interval(5, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                takePicture(imageCapture)
            }
    }

    private fun takePicture(imageCapture: ImageCapture) {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object: ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    imageAnalyzer.analyze(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    // TODO capture error handler
                }
            }
        )
    }

}