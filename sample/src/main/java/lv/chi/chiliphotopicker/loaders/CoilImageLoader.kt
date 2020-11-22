package lv.chi.chiliphotopicker.loaders

import android.content.Context
import android.net.Uri
import android.widget.ImageView
import coil.Coil
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import lv.chi.chiliphotopicker.R
import lv.chi.photopicker.loader.ImageLoader

class CoilImageLoader : ImageLoader {

    override fun loadImage(context: Context, view: ImageView, uri: Uri) {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .error(R.drawable.bg_placeholder)
            .fallback(R.drawable.bg_placeholder)
            .placeholder(R.drawable.bg_placeholder)
            .precision(Precision.AUTOMATIC)
            .scale(Scale.FIT)
            .size(450, 450)
            .target(view)
            .build()

        Coil.enqueue(request)
    }

}