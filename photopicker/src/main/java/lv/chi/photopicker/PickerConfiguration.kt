package lv.chi.photopicker

import lv.chi.photopicker.loader.ImageLoader

internal object PickerConfiguration {

    private lateinit var imageLoader: ImageLoader
    private lateinit var authority: String
    private var theme: Int = R.style.ChiliPhotoPicker_Light
    private var multiple = false
    private var allowCamera = true

    fun setImageLoader(imageLoader: ImageLoader) {
        this.imageLoader = imageLoader
    }

    fun setAuthority(authority: String) {
        this.authority = authority
    }

    fun setTheme(theme: Int) {
        this.theme = theme
    }

    fun allowMultiple(allow: Boolean) {
        multiple = allow
    }

    fun allowCamera(allow: Boolean) {
        allowCamera = allow
    }

    fun getImageLoader() = imageLoader
    fun getAuthority() = authority
    fun getTheme() = theme
    fun getAllowMultiple() = multiple
    fun getAllowCamera() = allowCamera
}