package lv.chi.photopicker

import androidx.annotation.StyleRes
import androidx.fragment.app.FragmentManager
import lv.chi.photopicker.loader.ImageLoader

object ChiliPhotoPickerBuilder {

    fun setImageLoader(loader: ImageLoader): ChiliPhotoPickerBuilder {
        PickerConfiguration.setImageLoader(loader)
        return this
    }

    fun setTheme(@StyleRes themeRes: Int): ChiliPhotoPickerBuilder {
        PickerConfiguration.setTheme(themeRes)
        return this
    }

    fun setAuthority(authority: String): ChiliPhotoPickerBuilder {
        PickerConfiguration.setAuthority(authority)
        return this
    }

    fun allowMultiple(allow: Boolean): ChiliPhotoPickerBuilder {
        PickerConfiguration.allowMultiple(allow)
        return this
    }

    fun allowCamera(allow: Boolean): ChiliPhotoPickerBuilder {
        PickerConfiguration.allowCamera(allow)
        return this
    }

    fun show(fm: FragmentManager, tag: String?) {
        PhotoPickerFragment.newInstance().show(fm, tag)
    }
}