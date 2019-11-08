package lv.chi.chiliphotopicker

import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import lv.chi.chiliphotopicker.loaders.GlideImageLoader
import lv.chi.photopicker.PhotoPickerFragment

class MainActivity : AppCompatActivity(), PhotoPickerFragment.Callback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        open_picker.setOnClickListener { openPicker() }
        picked_url.movementMethod = ScrollingMovementMethod()
    }

    override fun onImagesPicked(photos: ArrayList<Uri>) {
        picked_url.text = photos.joinToString(separator = "\n") { it.toString() }
    }

    private fun openPicker() {
        PhotoPickerFragment.newInstance(multiple = true, allowCamera = true)
            .imageLoader(GlideImageLoader())
            .setTheme(R.style.ChiliPhotoPicker_Dark)
            .authority("lv.chi.sample.fileprovider")
            .show(supportFragmentManager, "picker")
    }
}