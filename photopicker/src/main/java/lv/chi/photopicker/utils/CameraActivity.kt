package lv.chi.photopicker.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_camera.*
import lv.chi.photopicker.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*


internal class CameraActivity : AppCompatActivity() {
    private var permissionGranted: Boolean = false
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        permissionGranted = if (Build.VERSION.SDK_INT >= 23) {
            hasCameraPermission()
        } else true

        overridePendingTransition(R.anim.slide_up, R.anim.hold)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        capture.setOnClickListener { takePhoto() }
        close.setOnClickListener { finish() }

        outputDirectory = getOutputDirectory(baseContext)
        tryStartPreview()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Request.CAMERA_ACCESS_PERMISSION && hasCameraPermission()) {
            permissionGranted = true
            startPreview()
        } else onBackPressed()
    }


    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private object Request {
        const val CAMERA_ACCESS_PERMISSION = 2
    }


    override fun onResume() {
        super.onResume()
        if (imageCapture == null && permissionsGranted()) {
            startPreview()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.hold, R.anim.slide_down)
    }

    private fun tryStartPreview() {
        startPreview()
    }

    private fun startPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(preview.createSurfaceProvider())
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )

                // camera provides access to CameraControl & CameraInfo
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                // Attach the viewfinder's surface provider to preview use case

            } catch (exc: Exception) {
                Log.e("CameraActivity", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                "dd-M-yyyy hh:mm:ss", Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraActivity", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedImageBitmap = getBitmap(output.savedUri.toString())!!
                    val uri = getImageUri(baseContext, savedImageBitmap)
                    setResult(RESULT_OK, Intent().setData(uri))
                    finish()
                }
            })
    }

    private fun getBitmap(path: String): Bitmap? {
        val uri = Uri.fromFile(File(path))
        var inputStream: InputStream? = null
        try {
            val IMAGE_MAX_SIZE = 1200000 // 1.2MP
            inputStream = contentResolver.openInputStream(uri)

            // Decode image size
            var o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            BitmapFactory.decodeStream(inputStream, null, o)
            inputStream?.close()
            var scale = 1
            while (o.outWidth * o.outHeight * (1 / Math.pow(scale.toDouble(), 2.0)) >
                IMAGE_MAX_SIZE
            ) {
                scale++
            }
            Log.d(
                "",
                "scale = " + scale + ", orig-width: " + o.outWidth + ", orig-height: " + o.outHeight
            )
            var b: Bitmap? = null
            inputStream = contentResolver.openInputStream(uri)
            if (scale > 1) {
                scale--
                // scale to max possible inSampleSize that still yields an image
                // larger than target
                o = BitmapFactory.Options()
                o.inSampleSize = scale
                b = BitmapFactory.decodeStream(inputStream, null, o)

                // resize to desired dimensions
                val height = b!!.height
                val width = b.width
                Log.d("", "1th scale operation dimenions - width: $width, height: $height")
                val y = Math.sqrt(
                    IMAGE_MAX_SIZE
                            / (width.toDouble() / height)
                )
                val x = (y / height) * width
                val scaledBitmap = Bitmap.createScaledBitmap(
                    (b), x.toInt(),
                    y.toInt(), true
                )
                b.recycle()
                b = scaledBitmap
                System.gc()
            } else {
                b = BitmapFactory.decodeStream(inputStream)
            }
            inputStream?.close()
            Log.d(
                "", ("bitmap size - width: " + b!!.width + ", height: " +
                        b.height)
            )
            return b
        } catch (e: IOException) {
            return null
        }
    }

    fun getImageUri(inContext: Context, inImage: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path =
            MediaStore.Images.Media.insertImage(inContext.contentResolver, inImage, "Title", null)
        return Uri.parse(path)
    }

    private fun getOutputDirectory(context: Context): File {
        val appContext = context.applicationContext
        val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            File(it, "Pictures").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else appContext.filesDir
    }

    private fun permissionsGranted(): Boolean {
        return PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private val PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        fun createIntent(context: Context) = Intent(context, CameraActivity::class.java)
    }

    fun resizeBitmap(getBitmap: Bitmap, maxSize: Int): Bitmap? {
        var width = getBitmap.width
        var height = getBitmap.height
        val x: Double
        if (width >= height && width > maxSize) {
            x = width / height.toDouble()
            width = maxSize
            height = (maxSize / x).toInt()
        } else if (height >= width && height > maxSize) {
            x = height / width.toDouble()
            height = maxSize
            width = (maxSize / x).toInt()
        }
        return Bitmap.createScaledBitmap(getBitmap, width, height, false)
    }


}
