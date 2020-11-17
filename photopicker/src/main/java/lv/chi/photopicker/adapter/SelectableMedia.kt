package lv.chi.photopicker.adapter

import android.net.Uri
import java.util.concurrent.TimeUnit

internal data class SelectableMedia constructor(
    val id: Int,
    val type: Type,
    val uri: Uri,
    val selected: Boolean,
    val duration: Long? = null
) {

    enum class Type {
        IMAGE,
        VIDEO
    }

    fun getDuration(): String {
        if (duration == null) return String.format("00:%02d", 0)
        return try {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
            val seconds = duration % minutes.toInt()

            "${String.format("%02d", minutes)}:${String.format("%02d", seconds)}"
        } catch (exception: ArithmeticException) {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(duration)

            String.format("00:%02d", seconds)
        }
    }

}