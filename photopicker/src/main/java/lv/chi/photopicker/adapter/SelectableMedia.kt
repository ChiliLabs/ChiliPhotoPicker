package lv.chi.photopicker.adapter

import android.net.Uri


internal data class SelectableMedia(
    val id: Int,
    val uri: Uri,
    val selected: Boolean
)