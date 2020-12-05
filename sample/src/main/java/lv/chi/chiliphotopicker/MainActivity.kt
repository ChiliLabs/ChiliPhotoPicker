package lv.chi.chiliphotopicker

import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import lv.chi.photopicker.MediaPickerFragment
import lv.chi.photopicker.adapter.SelectableMedia

class MainActivity : AppCompatActivity(), MediaPickerFragment.Callback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        picked_url.movementMethod = ScrollingMovementMethod()

        open_picker.setOnClickListener { openPicker() }
    }

    override fun onMediaPicked(media: List<SelectableMedia>) {
        picked_url.text = media.joinToString(separator = "\n\n") { it.toString() }
    }

    override fun onCameraMediaPicked(media: SelectableMedia) {
        picked_url.text = media.toString()
    }

    override fun onGalleryMediaPicked(media: List<Uri>) {
        picked_url.text = media.joinToString(separator = "\n\n") { it.toString() }
    }

    private fun openPicker() {
        MediaPickerFragment.newInstance(
            multiple = true,
            allowCamera = true,
            maxSelection = 5,
            pickerMode = MediaPickerFragment.PickerMode.MEDIA,
            theme = R.style.SampleMediaPicker
        ).show(supportFragmentManager, "picker")
    }
}