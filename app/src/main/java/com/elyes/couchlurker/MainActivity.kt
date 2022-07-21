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
import androidx.lifecycle.lifecycleScope
import com.elyes.couchlurker.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        lifecycleScope.launch(Dispatchers.Default) {
            val isPermissionGranted = askForCameraPermission()

            if (isPermissionGranted.not()) {
                withContext(Dispatchers.Main) { finish() }
                return@launch
            }

            val imageCapture = prepareImageCapture()

            observeDetectionRequests(imageCapture)
        }
    }

    private fun askForCameraPermission(): Boolean {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        } else {
            var isPermissionGranted: Boolean? = null
            val requestPermissionLauncher =
                registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted: Boolean ->
                    isPermissionGranted = isGranted
                }
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)

            while (isPermissionGranted == null) {
                // wait for the user to decide
            }
            return isPermissionGranted!!
        }
    }

    private fun prepareImageCapture(): ImageCapture {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        var imageCapture: ImageCapture? = null

        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val imgCapture = ImageCapture.Builder().build()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(LENS_FACING_FRONT)
                    .build()

                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    imgCapture
                )

                imageCapture = imgCapture
            },
            mainExecutor
        )

        while (imageCapture == null) {
            // wait for camera to be prepared
        }
        return imageCapture!!
    }

    private suspend fun observeDetectionRequests(imageCapture: ImageCapture) {
        while (true) {
            delay(5000L)
            takePicture(imageCapture)
        }
    }

    private fun takePicture(imageCapture: ImageCapture) {
        imageCapture.takePicture(
            Dispatchers.Default.asExecutor(),
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