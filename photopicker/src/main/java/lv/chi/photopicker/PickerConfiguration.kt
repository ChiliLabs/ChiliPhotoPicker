package lv.chi.photopicker

import lv.chi.photopicker.loader.ImageLoader

internal object PickerConfiguration {

    private var imageLoader: ImageLoader? = null
    private var authority: String? = null

    fun setUp(imageLoader: ImageLoader, authority: String?) {
        this.imageLoader = imageLoader
        this.authority = authority
    }

    fun getImageLoader(): ImageLoader = imageLoader
        ?: throw IllegalStateException("imageLoader is null. You probably forget to call MediaPicker.init()")

    fun getAuthority(): String = authority
        ?: throw IllegalStateException("authority is null. You probably forget to pass it to MediaPicker.init()")

}