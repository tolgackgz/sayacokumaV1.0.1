package com.example.fizibil.sayacokuma

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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

import java.util.concurrent.Executors

import android.graphics.*
import android.widget.ImageView
import android.util.Size
import android.view.View



@SuppressLint("RestrictedApi")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: MainActivityBinding

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var imagePreview: Preview? = null

    private var cameraControl: CameraControl? = null

    private var cameraInfo: CameraInfo? = null

    private var linearZoom = 0f

    private var cameraProvider: ProcessCameraProvider? = null

    private var bmp: Bitmap? = null



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
                //setTargetAspectRatio(AspectRatio.RATIO_16_9)
                setTargetResolution(Size(1280, 720))
                //setTargetRotation(binding.previewView.display.rotation)
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
                    imagePreview
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

    private fun View.showOrGone(show: Boolean) {
        visibility = if(show) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun takePicture() {
        val view: View = findViewById(R.id.cameraSayacView)
        val imageView: ImageView = findViewById(R.id.imageView)
        val previewView: PreviewView = findViewById(R.id.previewView)

        stopCamera()
        val cameraBmp=previewView.bitmap

        var location = IntArray(2)
        view.getLocationOnScreen(location)
        val xx = location[0]
        val yy = location[1]
        println("View Koordinatları: $xx ve $yy")

        val fiziBmp =Bitmap.createBitmap(cameraBmp!!, xx,yy,view.width, view.height)

        runOnUiThread {
            imageView.setImageBitmap(fiziBmp)
        }


    }

    private fun stopCamera(){
        cameraProvider?.unbind(imagePreview)
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
