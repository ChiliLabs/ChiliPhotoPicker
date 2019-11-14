package lv.chi.photopicker.utils

import android.content.Context
import androidx.core.content.FileProvider
import lv.chi.photopicker.PickerConfiguration
import java.io.File

internal fun File.providerUri(context: Context) =
    FileProvider.getUriForFile(context, PickerConfiguration.getAuthority(), this)
