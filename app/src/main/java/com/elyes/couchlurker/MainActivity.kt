package com.elyes.couchlurker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.LENS_FACING_FRONT
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActivityMainBinding

    private val faceDetector = FaceDetection.getClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /**
         * We need to use CoroutineStart.UNDISPATCHED because askForCameraPermission needs to be
         * executed in onCreate, or more accurately before onStart, otherwise registerForActivityResult
         * will throw an error.
         */
        lifecycleScope.launch(context = Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            val isPermissionGranted = askForCameraPermission()

            if (isPermissionGranted.not()) {
                withContext(Dispatchers.Main) { finish() }
                return@launch
            }

            val imageCapture = prepareImageCapture()

            observeDetectionRequests(imageCapture)
        }
    }

    private suspend fun askForCameraPermission(): Boolean = suspendCoroutine { continuation ->
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            continuation.resume(true)
        } else {
            lifecycleScope.launch {
                val requestPermissionLauncher =
                    registerForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted: Boolean ->
                        continuation.resume(isGranted)
                    }
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private suspend fun prepareImageCapture(): ImageCapture = suspendCoroutine { continuation ->
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

                continuation.resume(imageCapture)
            },
            mainExecutor
        )
    }

    private suspend fun observeDetectionRequests(imageCapture: ImageCapture) {
        while (true) {
            delay(5000L)
            try {
                val image = takePicture(imageCapture)
                val faceCount = analyzeImage(image)
                Log.d(this.localClassName, "number of faces: $faceCount")
            } catch (error: Throwable) {

            }
        }
    }

    private suspend fun takePicture(imageCapture: ImageCapture): ImageProxy = suspendCoroutine { continuation ->
        imageCapture.takePicture(
            Dispatchers.Default.asExecutor(),
            object: ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    continuation.resume(image)
                }
                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            }
        )
    }

    private suspend fun analyzeImage(imageProxy: ImageProxy): Int = suspendCoroutine { continuation ->
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            continuation.resumeWithException(Throwable("ImageProxy.image is null"))
            return@suspendCoroutine
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        faceDetector.process(inputImage)
            .addOnSuccessListener {
                continuation.resume(it.size)
            }
            .addOnFailureListener {
                continuation.resumeWithException(it)
            }

        imageProxy.close()
    }

}