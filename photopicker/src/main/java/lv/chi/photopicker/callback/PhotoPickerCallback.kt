package lv.chi.photopicker.callback

import android.net.Uri

interface PhotoPickerCallback {
    fun onImagesPicked(photos: ArrayList<Uri>)
}