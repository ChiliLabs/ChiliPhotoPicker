package lv.chi.photopicker.adapter

import android.net.Uri

internal data class SelectableImage(
    val id: Int,
    val uri: Uri,
    val selected: Boolean
)