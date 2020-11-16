package lv.chi.chiliphotopicker

import android.app.Application
import lv.chi.chiliphotopicker.loaders.GlideImageLoader
import lv.chi.photopicker.MediaPicker

class App: Application() {

    override fun onCreate() {
        super.onCreate()

        MediaPicker.init(
            loader = GlideImageLoader(),
            authority = "lv.chi.sample.fileprovider"
        )
    }
}