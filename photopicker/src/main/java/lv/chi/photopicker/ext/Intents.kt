package lv.chi.photopicker.ext

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

internal class Intents {
    companion object {
        fun getCameraResult(data: Intent?): Uri? =
            data?.getParcelableExtra(MediaStore.EXTRA_OUTPUT)

        fun getUriResult(data: Intent?): List<Uri>? {
            return data?.data?.let { listOf(it) } ?: data?.clipData?.let {
                (0 until it.itemCount).map { i -> it.getItemAt(i).uri }
            }
        }
    }
}