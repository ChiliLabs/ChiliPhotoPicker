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
        open_picker.setOnClickListener { openPicker() }
        picked_url.movementMethod = ScrollingMovementMethod()
    }

    override fun onMediaPicked(mediaItems: ArrayList<Uri>) {
        picked_url.text = mediaItems.joinToString(separator = "\n") { it.toString() }
    }

    private fun openPicker() {
        MediaPickerFragment.newInstance(
            multiple = true,
            allowCamera = true,
            maxSelection = 5,
            pickerType = MediaPickerFragment.PickerType.ANY,
            theme = R.style.ChiliPhotoPicker_Dark
        ).show(supportFragmentManager, "picker")
    }
}