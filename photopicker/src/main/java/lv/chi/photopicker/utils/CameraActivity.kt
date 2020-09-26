package lv.chi.photopicker.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

internal class CameraActivity : AppCompatActivity() {

    private lateinit var output: Uri

    private var permissionGranted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 23) {
            permissionGranted = hasCameraPermission()
        } else permissionGranted = true

        if (savedInstanceState == null) {
            output = provideImageUri()
            if (permissionGranted) requestImageCapture()
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
        if (requestCode == Request.IMAGE_CAPTURE) {
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
            requestImageCapture()
        } else onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(Key.OUTPUT, output)
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            .toString() + "/picFolder/"
        val newdir = File(dir)
        newdir.mkdirs()
    }

    private fun provideImageUri() = createTempFile(
        suffix = ".jpg",
        directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "camera"
        ).apply { mkdirs() }
    )
        .apply { deleteOnExit() }
        .providerUri(this)

    private fun requestImageCapture() =
        startActivityForResult(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, output)
                .also { intent ->
                    grantUriPermission(
                        intent.resolveActivity(packageManager).packageName,
                        output,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                },
            Request.IMAGE_CAPTURE
        )

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private object Request {
        const val IMAGE_CAPTURE = 1
        const val CAMERA_ACCESS_PERMISSION = 2
    }

    private object Key {
        const val OUTPUT = "output"
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, CameraActivity::class.java)
    }
}