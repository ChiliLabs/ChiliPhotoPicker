package lv.chi.photopicker.loader

import android.content.Context
import android.net.Uri
import android.widget.ImageView

interface ImageLoader {
    fun loadImage(context: Context, view: ImageView, uri: Uri)
}