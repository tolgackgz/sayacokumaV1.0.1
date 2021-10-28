package com.example.fizibil.sayacokuma

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.StateSet.TAG
import androidx.core.content.ContextCompat
import com.example.fizibil.sayacokuma.databinding.MainActivityBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SuppressLint("RestrictedApi")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: MainActivityBinding

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var imagePreview: Preview? = null

    private var imageAnalysis: ImageAnalysis? = null

    private var imageCapture: ImageCapture? = null

    private var cameraControl: CameraControl? = null

    private var cameraInfo: CameraInfo? = null

    private var linearZoom = 0f

    private var cameraProvider: ProcessCameraProvider? = null

    private var bitmapCapture: Bitmap? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }


        binding.cameraCaptureButton.setOnClickListener {
            takePicture()
            if (bitmapCapture!== null) {
                cameraProvider?.unbindAll()
            }
        }
        initCameraModeSelector()
        binding.cameraTorchButton.setOnClickListener {
            toggleTorch()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProvider = cameraProviderFuture.get()
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        cameraProviderFuture.addListener({
            imagePreview = Preview.Builder().apply {
                setTargetAspectRatio(AspectRatio.RATIO_16_9)
                //setTargetRotation(binding.previewView.display.rotation)
            }.build()

            imageAnalysis = ImageAnalysis.Builder().apply {
                setImageQueueDepth(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                setTargetResolution(Size(1280, 720))
            }.build()
            imageAnalysis?.setAnalyzer(cameraExecutor, LuminosityAnalyzer())

            imageCapture = ImageCapture.Builder().apply {
                setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                //setFlashMode(ImageCapture.FLASH_MODE_AUTO)
            }.build()


            val cameraProvider = cameraProviderFuture.get()
            val camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                imagePreview,
                imageAnalysis,
                imageCapture
            )
            binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            imagePreview?.setSurfaceProvider(binding.previewView.surfaceProvider)
            cameraControl = camera.cameraControl
            cameraInfo = camera.cameraInfo
            setTorchStateObserver()
            setZoomStateObserver()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setTorchStateObserver() {
        cameraInfo?.torchState?.observe(this, { state ->
            if (state == TorchState.ON) {
                binding.cameraTorchButton.setImageDrawable(
                    ContextCompat.getDrawable(
                        this,
                        R.drawable.ic_flashlight_off_24dp
                    )
                )
            } else {
                binding.cameraTorchButton.setImageDrawable(
                    ContextCompat.getDrawable(
                        this,
                        R.drawable.ic_flashlight_on_24dp
                    )
                )
            }
        })
    }

    private fun setZoomStateObserver() {
        cameraInfo?.zoomState?.observe(this, { state ->
            // state.linearZoom
            // state.zoomRatio
            // state.maxZoomRatio
            // state.minZoomRatio
            Log.d(TAG, "${state.linearZoom}")
        })
    }

    private fun initCameraModeSelector() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {}

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    SAYAC -> {
                        binding.cameraCaptureButton.setOnClickListener {
                            takePicture()
                        }
                    }
                    TEST -> {
                        binding.cameraCaptureButton.setOnClickListener {

                        }
                    }
                }
            }

        })
    }

    private fun takePicture() {
        imageCapture?.takePicture( cameraExecutor, object :
            ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)
                bitmapCapture = imageProxyToBitmap(image)
                Log.i("TAKE BITMAP","BITMAP GORUNTU ELDE EDILDI")
            }

            override fun onError(exception: ImageCaptureException) {
                val msg = "Sayaç Yakalanamadı: ${exception.message}"
                binding.previewView.post {
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    /**
     *  convert image proxy to bitmap
     *  @param image
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun toggleTorch() {
        if (cameraInfo?.torchState?.value == TorchState.ON) {
            cameraControl?.enableTorch(false)
        } else {
            cameraControl?.enableTorch(true)
        }
    }

    // Manage camera Zoom
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (linearZoom <= 0.9) {
                    linearZoom += 0.1f
                }
                cameraControl?.setLinearZoom(linearZoom)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (linearZoom >= 0.1) {
                    linearZoom -= 0.1f
                }
                cameraControl?.setLinearZoom(linearZoom)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private class LuminosityAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalyzedTimestamp = 0L

        /**
         * Helper extension function used to extract a byte array from an
         * image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {
            image.imageInfo.rotationDegrees
            val currentTimestamp = System.currentTimeMillis()
            // Calculate the average luma no more often than every second
            if (currentTimestamp - lastAnalyzedTimestamp >=
                TimeUnit.SECONDS.toMillis(1)
            ) {
                // Since format in ImageAnalysis is YUV, image.planes[0]
                // contains the Y (luminance) plane
                val buffer = image.planes[0].buffer
                // Extract image data from callback object
                val data = buffer.toByteArray()
                // Convert the data into an array of pixel values
                val pixels = data.map { it.toInt() and 0xFF }
                // Compute average luminance for the image
                val luma = pixels.average()
                // Log the new luma value
                Log.d("CameraXApp", "Average luminosity: $luma")
                // Update timestamp of last analyzed frame
                lastAnalyzedTimestamp = currentTimestamp
            }
            image.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }


    companion object {

        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        private const val SAYAC = 0
        private const val TEST = 1
    }
}
