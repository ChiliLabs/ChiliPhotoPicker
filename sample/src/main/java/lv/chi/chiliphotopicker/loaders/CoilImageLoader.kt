package lv.chi.chiliphotopicker.loaders

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import coil.load
import lv.chi.photopicker.loader.ImageLoader

class CoilImageLoader: ImageLoader {
    override fun loadImage(context: Context, view: ImageView, uri: Uri) {
        view.load(uri)
    }
}
