package com.example.fizibil.sayacokuma

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.AssetManager
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
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.core.Rect
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.ArrayList
import android.R.attr.bitmap
import android.view.TextureView
import com.google.mlkit.vision.common.InputImage

import org.opencv.core.CvType

import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer


//@SuppressLint("RestrictedApi")
class MainActivity : AppCompatActivity() {
    private lateinit var interpreter: Interpreter

    private lateinit var binding: MainActivityBinding

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var imagePreview: Preview? = null

    private var imageCapture: ImageCapture? = null

    private var cameraControl: CameraControl? = null

    private var cameraInfo: CameraInfo? = null

    private var linearZoom = 0f

    private var cameraProvider: ProcessCameraProvider? = null

    private var bmp: Bitmap? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initOpenCV()

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
            stopCamera()
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProvider = cameraProviderFuture.get()

        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        cameraProviderFuture.addListener({
            imagePreview = Preview.Builder().apply {
                setTargetAspectRatio(AspectRatio.RATIO_16_9)
                //setTargetResolution(Size(1280, 720))
                //setTargetRotation(binding.previewView.display.rotation)
            }.build()

            imageCapture = ImageCapture.Builder().apply {
                setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                setTargetAspectRatio(AspectRatio.RATIO_16_9)
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
                    imageCapture
                )
                binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                imagePreview?.setSurfaceProvider(binding.previewView.surfaceProvider)
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                setTorchStateObserver()

            } catch (exc: Exception) {
                binding.previewView.post {Toast.makeText(this, "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show()}
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
        imageCapture?.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            @SuppressLint("UnsafeOptInUsageError")
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val previewView: PreviewView = findViewById(R.id.previewView)
                val imageView: ImageView = findViewById(R.id.imageView)
                val view: View = findViewById(R.id.cameraSayacView)

                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val cameraImg = imageProxy.image

                val inputImage = InputImage.fromMediaImage(cameraImg, rotationDegrees).bitmapInternal

                val fiziBmp =croppedImage(inputImage, previewView, view, 0f)

                fiziMeterCV(fiziBmp)

                runOnUiThread {
                    imageView.setImageBitmap(fiziBmp)
                }

                imageProxy.close()
            }

            override fun onError(exception: ImageCaptureException) {
                val msg = "Photo capture failed: ${exception.message}"
                binding.previewView.post {
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
        })


    }

    private fun fiziMeterCV(fiziBmp: Bitmap ){

        try {
            val img = Mat(fiziBmp.height, fiziBmp.width, CvType.CV_64FC3)
            Utils.bitmapToMat(fiziBmp, img)
            Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGB)

            val roiI = extractRoi(
                "s1_MAY22.tflite",
                img,
                1.0,
                0.12,
                550.0,
                300.0,
                192,
                304,
                1
            )

            val roiII = extractRoi(
                "s2_MAY22.tflite",
                roiI,
                1.0,
                1.0,
                550.0,
                300.0,
                48,
                464,
                2
            )

            val roiIICrop = Rect(
                0,
                (roiII.height() * 0.13).toInt(),
                roiII.width(),
                roiII.height() - 2 * (roiII.height() * 0.13).toInt()
            )

            val roiIICr = roiII.submat(roiIICrop)

            val newroiII = roiIICr.clone()

            val rBmp = Bitmap.createBitmap(roiIICr.cols(), roiIICr.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(roiIICr, rBmp)

            val probMat = extractDigits(
                "s3_MAY22.tflite",
                newroiII,
                128,
                32
            )

            val digits = extractDigit(
                roiII,
                probMat,
                128.0,
                32.0
            )

            val fiziMeterReader = digitReader(digits, "s4_21EYL_1.tflite")

            binding.previewView.post {
                Toast.makeText(
                    applicationContext, "OKUNAN RAKAM: $fiziMeterReader",
                    Toast.LENGTH_SHORT
                ).show()
            } // butona basınca process başlasın ve bu mesajı göstersin

            //textView.text = sayacOku.toString()
        } catch(exception: CvException){
            binding.previewView.post {
                Toast.makeText(
                    applicationContext, "SAYAÇ YAKALANAMADI",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }catch(exception: IndexOutOfBoundsException){
            binding.previewView.post {
                Toast.makeText(
                    applicationContext, "SAYAÇ YAKALANAMADI",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun stopCamera(){
        cameraProvider?.unbind(imagePreview)
    }

    private fun croppedImage(source: Bitmap, frame: PreviewView, reference: View, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)

        val heightOriginal = frame.height
        val widthOriginal = frame.width
        val heightFrame = reference.height
        val widthFrame = reference.width
        val leftFrame = reference.left
        val topFrame = reference.top
        val heightReal = source.height
        val widthReal = source.width
        val widthFinal = widthFrame * widthReal / widthOriginal
        val heightFinal = heightFrame * heightReal / heightOriginal
        val leftFinal = leftFrame * widthReal / widthOriginal
        val topFinal = topFrame * heightReal / heightOriginal

        return Bitmap.createBitmap(
            source,
            leftFinal, topFinal, widthFinal, heightFinal, matrix, true
        )
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

    private fun getIndexOfLargest(array: FloatArray?): Int {
        if (array == null || array.isEmpty()) return -1 // null or empty
        var largest = 0
        for (i in 1 until array.size) {
            if (array[i] > array[largest]) largest = i
        }
        return largest // position of the first largest found
    }

    private fun digitReader(digits: HashMap<Int,Mat>,mModelPath: String): List<String> {

        var sayacOku= listOf("SAYAÇ DEĞERİ:")
        for (i in 0 until 5) {
            val digit = digits[i]
            Imgproc.resize(digit, digit, Size(25.0, 32.0))

            val roiBmp =
                Bitmap.createBitmap(digit!!.cols(), digit.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(digit, roiBmp)

            initInterpreter(mModelPath)

            val processedTensor = tensorProcess(roiBmp,25,32,"float32")

            val probabilityBuffer =
                TensorBuffer.createFixedSize(intArrayOf(1, 10), DataType.FLOAT32)

            interpreter.run(processedTensor!!.buffer, probabilityBuffer.buffer)

            val maxIdx = getIndexOfLargest(probabilityBuffer.floatArray)
            Log.i("DIGIT OUTPUT:", "$maxIdx")
            sayacOku+=maxIdx.toString()

        }
        return sayacOku
    }


    private fun extractDigit(oldMat: Mat, probMat: HashMap<Int, Mat>, targetW: Double, targetH: Double): HashMap<Int, Mat> {
        var digitMap=HashMap<Int, Mat>()
        for (i in 0 until  probMat.size) {
            var mask = Mat()
            Imgproc.resize(
                probMat[i]!!,
                probMat[i]!!,
                Size(oldMat.width().toDouble(), oldMat.height().toDouble())
            ) //128,32
            probMat[i]!!.convertTo(mask, CvType.CV_8U)
            //Log.i("MASK MATRIX:", mask.dump())

            val mHierarchy = Mat()
            val contours: List<MatOfPoint> = ArrayList()

            Imgproc.findContours(
                mask,
                contours,
                mHierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val rect = Imgproc.boundingRect(contours[0])
            var xx = rect.x
            var yy = rect.y
            val ww = rect.width
            val hh = rect.height


            val border = 3

            if (xx < 3) {
                xx += border
            }
            if (yy < 3) {
                yy += border
            }

            try {
                val rectDigit = Rect(xx - border, yy - border, ww + border, hh + border)
                digitMap[i] = oldMat.submat(rectDigit)}

            finally {
                val rectDigit = Rect(xx, yy, ww, hh)
                digitMap[i] = oldMat.submat(rectDigit)}
        }
        return digitMap
    }

    private fun extractDigits(
        modelPath: String,
        image: Mat,
        targetW: Int,
        targetH: Int ): HashMap<Int, Mat>
    {

        val roiBmp = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(image, roiBmp)

        initInterpreter(modelPath)

        val processedTensor = tensorProcess(roiBmp,targetH,targetW,"float32")

        // The shape of *1* output's tensor
        var outputShape: IntArray
        // The type of the *1* output's tensor
        var outputDataType: DataType
        // The multi-tensor ready storage
        val outputProbabilityBuffers = HashMap<Int, TensorBuffer>()
        // For each model's tensors (there are getOutputTensorCount() of them for this tflite model)
        for (i in 0 until interpreter.outputTensorCount) {
            outputShape = interpreter.getOutputTensor(i).shape()
            outputDataType = interpreter.getOutputTensor(i).dataType()
            outputProbabilityBuffers[i] = TensorBuffer.createFixedSize(outputShape, outputDataType)
        }

        interpreter.runForMultipleInputsOutputs(
            arrayOf(processedTensor!!.buffer),
            mapOf(
                0 to outputProbabilityBuffers[0]!!.buffer,
                1 to outputProbabilityBuffers[1]!!.buffer,
                2 to outputProbabilityBuffers[2]!!.buffer,
                3 to outputProbabilityBuffers[3]!!.buffer,
                4 to outputProbabilityBuffers[4]!!.buffer
            )
        )
        return hashMapToMat(outputProbabilityBuffers)
    }


    private fun hashMapToMat(probMap: HashMap<Int, TensorBuffer>): HashMap<Int, Mat> {
        val emptyMap= HashMap<Int, Mat>()
        for (l in 0 until probMap.size) {

            var output=probMap[l]!!.floatArray.clone()

            var sayac=0
            for (i in output.indices) {

                if (output[i] > 0.5) {
                    output[i] = 1.0F
                    sayac += 1
                } else {
                    output[i] = 0.0F
                }
            }

            val pToMat = Mat(32, 128, CvType.CV_32F)
            pToMat.put(0, 0, output)
            emptyMap[l]= pToMat
        }
        return emptyMap
    }

    private fun extractRoi(
        modelPath: String,
        image: Mat,
        tolX: Double,
        tolY: Double,
        resizeW: Double,
        resizeH: Double,
        resizeRows: Int,
        resizeCols: Int,
        phase: Int): Mat {

        if (phase==1) {
            Imgproc.resize(image, image, Size(resizeW, resizeH))
        }

        val oldImg= image.clone()
        val cImg = image.clone()

        image.convertTo(image, CvType.CV_64FC3)

        val size = (image.total() * image.channels()).toInt()
        val temp = DoubleArray(size)

        image[0, 0, temp]
        for (i in 0 until size) temp[i] = temp[i]

        cImg.put(0, 0, *temp)

        val bmp = Bitmap.createBitmap(cImg.cols(), cImg.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(cImg, bmp)

        initInterpreter(modelPath)

        val probToMat = Mat(resizeRows, resizeCols, CvType.CV_32F)
        probToMat.put(0, 0, phaseTFLite(bmp,resizeRows,resizeCols))

        val imgt1  = Mat()
        val dst  = Mat()

        Core.compare(probToMat, Scalar(0.5), imgt1, Core.CMP_GT)
        Core.normalize(imgt1,dst,0.0,1.0, Core.NORM_MINMAX, CvType.CV_32F)

        dst.convertTo(dst, CvType.CV_8U)
        val numLabels: Int = Imgproc.connectedComponents(dst, dst)

        var maxVal = -1
        var maxCount = -1

        val sizeDst: Int = (dst.total() * dst.channels()).toInt()

        val buff = IntArray(sizeDst)
        dst.get(0, 0, buff)

        for (i in 0 until numLabels) {
            if ((buff.filter { it==i }).sum() > maxCount){
                maxCount = (buff.filter { it==i }).sum()
                maxVal = i
            }
        }

        val copyBuff=buff.map {
            if (it==maxVal) 1 else 0
        }

        val bv = Mat(dst.size(), CvType.CV_32S)
        bv.put(0, 0, copyBuff.toIntArray())

        val resultMat= Mat(dst.size(), CvType.CV_8U)
        Core.multiply(dst,bv,resultMat)

        resultMat.convertTo(resultMat, CvType.CV_64F)

        val resizeImage = Mat()
        val sz = Size(resizeW, resizeH)
        Imgproc.resize(resultMat, resizeImage, sz)

        resizeImage.convertTo(resizeImage, CvType.CV_8U)

        val mHierarchy = Mat()
        val contours: List<MatOfPoint> = ArrayList()

        Imgproc.findContours(
            resizeImage,
            contours,
            mHierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val rect = Imgproc.boundingRect(contours[0])
        val xx = rect.x
        val yy = rect.y
        val ww = rect.width
        val hh = rect.height

        val toleranceX = (kotlin.math.abs(ww) * tolX).toInt() //uzunluk için tolerans
        val toleranceY = (kotlin.math.abs(hh) * tolY).toInt() //yükseklik için tolerans

        return (if (phase==1) {
            val rectCrop = Rect(0, yy - toleranceY, cImg.width(), hh + toleranceY)
            oldImg.submat(rectCrop)
        } else {
            val rectCrop = Rect(xx, 0, ww, cImg.height())
            oldImg.submat(rectCrop)
        })

    }

    private fun phaseTFLite(bmp:Bitmap,resizeRows: Int,resizeCols:Int): FloatArray {

        val processedTensor = tensorProcess(bmp,resizeRows,resizeCols,"uint8")

        val probabilityBuffer =
            TensorBuffer.createFixedSize(intArrayOf(1 , resizeRows , resizeCols , 1), DataType.FLOAT32)

        interpreter.run(processedTensor!!.buffer, probabilityBuffer.buffer)

        return probabilityBuffer.floatArray
    }

    private fun Tensor.detail(): String {
        return "[shape: ${this.shape().toList()} dataType: ${this.dataType()}, bytes: ${this.numBytes()}]"
    }

    private fun Interpreter.detail(): String {
        val sb = StringBuilder("interpreter: \n")
        sb.append("input: { \n")
        for (i in 0 until this.inputTensorCount) {
            sb.append("    ").append(this.getInputTensor(i).detail()).append("\n")
        }
        sb.append("}, \n")

        sb.append("output: { \n")
        for (i in 0 until this.outputTensorCount) {
            sb.append("    ").append(this.getOutputTensor(i).detail()).append("\n")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun tensorProcess(bmp:Bitmap,resizeRows: Int,resizeCols:Int,dataType:String): TensorBuffer? {

        val imageProcessor = ImageProcessor.Builder() //TFLite interpreter image processorü builder'ı çağır

            .add( ResizeOp( resizeRows , resizeCols , ResizeOp.ResizeMethod.BILINEAR ) ) //  Bilinear veya En Yakın Komşuluk metotları ile görüntü verisini yeniden boyutlandır
            .add( ResizeWithCropOrPadOp( resizeRows , resizeCols ) )
            //.add( Rot90Op() ) // Görüntüyü Çevir
            .build() //Build et


        val tensorImage = if (dataType=="float32") {
            TensorImage(DataType.FLOAT32)//Görüntü Tensorü FLOAT32

        } else {
            TensorImage(DataType.UINT8) }

        // Load the Bitmap
        tensorImage.load( bmp )  //Bitmap formatındaki görüntü verisini yükle
        // Process the image
        val processedImage = imageProcessor.process( tensorImage ) //Tensor image'ı build edilen processe sok

        val imageTensorBuffer = processedImage.tensorBuffer

        val tensorProcessor = TensorProcessor.Builder()
            .add( CastOp( DataType.FLOAT32 ) )
            .build()

        return tensorProcessor.process( imageTensorBuffer )
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

    ////////// ***** //////////
    private fun initInterpreter(mModelPath:String){
        val options = Interpreter.Options()
        options.setNumThreads(5)
        options.setUseNNAPI(true)
        interpreter = Interpreter(loadModelFile(assets, mModelPath), options)

    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    companion object {

        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        private const val SAYAC = 0
        private const val TEST = 1
    }
}
