package lv.chi.chiliphotopicker

import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import lv.chi.photopicker.MediaPickerFragment

class MainActivity : AppCompatActivity(), MediaPickerFragment.Callback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        picked_url.movementMethod = ScrollingMovementMethod()

        open_picker.setOnClickListener { openPicker() }
    }

    override fun onMediaPicked(media: List<Uri>) {
        picked_url.text = media.joinToString(separator = "\n") { it.toString() }
    }

    private fun openPicker() {
        MediaPickerFragment.newInstance(
            multiple = true,
            allowCamera = true,
            maxSelection = 5,
            pickerMode = MediaPickerFragment.PickerMode.ANY,
            theme = R.style.MediaPicker_Dark
        ).show(supportFragmentManager, "picker")
    }
}