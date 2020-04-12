package lv.chi.photopicker.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

internal class CameraActivity : AppCompatActivity() {

    companion object {
        private lateinit var captureMode: CaptureMode
        fun createIntent(context: Context, mode: CaptureMode): Intent {
            captureMode = mode
            return Intent(context, CameraActivity::class.java)
        }
    }

    private lateinit var output: Uri

    private var permissionGranted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 23) {
            permissionGranted = hasCameraPermission()
        } else permissionGranted = true

        if (savedInstanceState == null) {
            output = if (captureMode == CaptureMode.Photo) provideImageUri() else provideVideoUri()
            if (permissionGranted) requestMediaCapture()
            else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    Request.CAMERA_ACCESS_PERMISSION
                )
            }
        } else savedInstanceState.getParcelable<Uri>(Key.OUTPUT)?.let {
            output = it
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Request.MEDIA_CAPTURE) {
            if (resultCode == RESULT_OK) {
                setResult(RESULT_OK, Intent().setData(output))
            } else {
                contentResolver.delete(output, null, null)
            }
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Request.CAMERA_ACCESS_PERMISSION && hasCameraPermission()) {
            permissionGranted = true
            requestMediaCapture()
        } else onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(Key.OUTPUT, output)
    }

    private fun provideImageUri() = createTempFile(
        suffix = ".jpg",
        directory = File(this.cacheDir, "camera").apply { mkdirs() }
    )
        .apply { deleteOnExit() }
        .providerUri(this)

    private fun provideVideoUri() = createTempFile(
        suffix = ".mp4",
        directory = File(this.cacheDir, "camera").apply { mkdirs() }
    )
        .apply { deleteOnExit() }
        .providerUri(this)

    private fun requestMediaCapture() {
        val cameraIntent = if (captureMode == CaptureMode.Photo)
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        else
            Intent(
                MediaStore.ACTION_VIDEO_CAPTURE
            )

        cameraIntent.also { intent ->
            intent.putExtra(MediaStore.EXTRA_OUTPUT, output)

            grantUriPermission(
                intent.resolveActivity(packageManager).packageName,
                output,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            startActivityForResult(cameraIntent, Request.MEDIA_CAPTURE)
        }
    }


    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private object Request {
        const val MEDIA_CAPTURE = 1
        const val CAMERA_ACCESS_PERMISSION = 2
    }

    enum class CaptureMode {
        Photo,
        Video
    }

    private object Key {
        const val OUTPUT = "output"
    }
}