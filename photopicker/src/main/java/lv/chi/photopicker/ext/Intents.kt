package lv.chi.photopicker.ext

import android.content.Intent
import android.net.Uri

internal class Intents {
    companion object {
        fun getUriResult(data: Intent?): ArrayList<Uri>? {
            return data?.data?.let { arrayListOf(it) } ?:
            data?.clipData?.let {
                (0 until it.itemCount).map { i -> it.getItemAt(i).uri }
            }?.let { ArrayList(it) }
        }
    }
}