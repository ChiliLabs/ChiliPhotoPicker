package lv.chi.photopicker.utils

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File

internal object FileProviders {
    lateinit var authority: String
}

internal fun File.providerUri(context: Context) =
    FileProvider.getUriForFile(context, FileProviders.authority, this)
