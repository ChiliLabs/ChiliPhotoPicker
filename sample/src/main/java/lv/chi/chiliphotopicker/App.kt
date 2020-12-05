package lv.chi.chiliphotopicker

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.fetch.VideoFrameFileFetcher
import coil.fetch.VideoFrameUriFetcher
import lv.chi.chiliphotopicker.loaders.CoilImageLoader
import lv.chi.photopicker.MediaPicker

class App: Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .allowHardware(true)
            .availableMemoryPercentage(0.25)
            .bitmapConfig(if (Build.VERSION.SDK_INT >= 26) Bitmap.Config.HARDWARE else Bitmap.Config.ARGB_8888)
            .componentRegistry {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder())
                } else {
                    add(GifDecoder())
                }

                add(SvgDecoder(this@App))

                add(VideoFrameFileFetcher(this@App))
                add(VideoFrameUriFetcher(this@App))
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        MediaPicker.init(
            loader = CoilImageLoader(),
//            loader = GlideImageLoader(),
//            loader = PicassoImageLoader(),
            authority = "lv.chi.sample.fileprovider"
        )
    }

}