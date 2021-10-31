package com.example.fizibil.sayacokuma

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*

import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.StateSet.TAG
import androidx.core.content.ContextCompat
import com.example.fizibil.sayacokuma.databinding.MainActivityBinding
import com.google.android.material.tabs.TabLayout
import org.opencv.android.InstallCallbackInterface
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import java.nio.ByteBuffer

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import android.graphics.*
import android.view.Surface
import android.view.Surface.ROTATION_90
import android.widget.ImageView

import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import android.R.attr.data
import org.opencv.core.CvException

import org.opencv.core.Scalar


@SuppressLint("RestrictedApi")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: MainActivityBinding

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var imagePreview: Preview? = null

    //private var imageAnalysis: ImageAnalysis? = null

    private var imageCapture: ImageCapture? = null

    private var cameraControl: CameraControl? = null

    private var cameraInfo: CameraInfo? = null

    private var linearZoom = 0f

    private var cameraProvider: ProcessCameraProvider? = null

    private var bitmap: Bitmap? = null

    private var takeButton:Boolean = false



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
            takeButton=true
            takePicture()
        }
        initCameraModeSelector()
        binding.cameraTorchButton.setOnClickListener {
            toggleTorch()
        }

        initOpenCV()

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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)


        cameraProvider = cameraProviderFuture.get()

        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        cameraProviderFuture.addListener({
            imagePreview = Preview.Builder().apply {
                setTargetAspectRatio(AspectRatio.RATIO_16_9)
                //setTargetRotation(binding.previewView.display.rotation)
            }.build()


            /*imageAnalysis = ImageAnalysis.Builder().apply {
                setImageQueueDepth(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                setTargetResolution(Size(1280, 720))
                setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            }.build()
            imageAnalysis?.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { image ->
                //takeGasMeter(image,takeButton)
                image.close()

            }) */

            imageCapture = ImageCapture.Builder().apply {
                setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                //setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                setTargetRotation(ROTATION_90)
            }.build()


            val cameraProvider = cameraProviderFuture.get()

            // Must unbind the use-cases before rebinding them
            cameraProvider.unbindAll()
            try {
                // A variable number of use-cases can be passed here -
                // camera provides access to CameraControl & CameraInfo
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    imagePreview,
                    //imageAnalysis,
                    imageCapture
                )
                binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                imagePreview?.setSurfaceProvider(binding.previewView.surfaceProvider)
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                setTorchStateObserver()
                setZoomStateObserver()
            } catch (exc: Exception) {
                Toast.makeText(this, "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show()
            }


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
                            takeButton=true
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
        imageCapture?.takePicture(cameraExecutor, object :
            ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                //get bitmap from image
                val matSayac = imageProxyToMat(image)

                var bmp: Bitmap? = null
                try {
                    bmp = Bitmap.createBitmap(matSayac.cols(), matSayac.rows(), Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(matSayac, bmp)
                } catch (e: CvException) {
                    Log.d("Exception", e.message!!)
                }


                val imageView: ImageView = findViewById(R.id.imageView)
                runOnUiThread {
                    imageView.setImageBitmap(bmp)
                    cameraProvider?.unbindAll()
                }
                super.onCaptureSuccess(image)
            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
            }

        })


    }
    /**
     *  convert image proxy to bitmap
     *  @param image
     */
    private fun imageProxyToMat(image: ImageProxy): Mat {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val mat = Mat(image.width, image.height, CvType.CV_64F)
        mat.put(0, 0, data.toDouble())

        return mat
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

    ////////// OPENCV BASLATMA //////////
    private fun initOpenCV() {
        val engineInitialized = OpenCVLoader.initDebug()
        if (engineInitialized){
            Log.i(ContentValues.TAG, "The OpenCV was successfully initialized in debug mode using .so libs.")
        } else {
            OpenCVLoader.initAsync(
                OpenCVLoader.OPENCV_VERSION_3_4_0,
                this,
                object : LoaderCallbackInterface {
                    override fun onManagerConnected(status: Int) {
                        when (status) {
                            LoaderCallbackInterface.SUCCESS -> Log.d(
                                ContentValues.TAG,
                                "OpenCV successfully started."
                            )
                            LoaderCallbackInterface.INIT_FAILED -> Log.d(
                                ContentValues.TAG,
                                "Failed to start OpenCV."
                            )
                            LoaderCallbackInterface.MARKET_ERROR -> Log.d(
                                ContentValues.TAG,
                                "Google Play Store could not be invoked. Please check if you have the Google Play Store app installed and try again."
                            )
                            LoaderCallbackInterface.INSTALL_CANCELED -> Log.d(
                                ContentValues.TAG,
                                "OpenCV installation has been cancelled by the user."
                            )
                            LoaderCallbackInterface.INCOMPATIBLE_MANAGER_VERSION -> Log.d(
                                ContentValues.TAG,
                                "This version of OpenCV Manager is incompatible. Possibly, a service update is required."
                            )
                        }
                    }

                    override fun onPackageInstall(
                        operation: Int,
                        callback: InstallCallbackInterface?
                    ) {
                        Log.d(
                            ContentValues.TAG,
                            "OpenCV Manager successfully installed from Google Play."
                        )
                    }
                })
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
