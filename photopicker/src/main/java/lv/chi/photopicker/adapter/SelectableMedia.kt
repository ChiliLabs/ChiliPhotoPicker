package lv.chi.photopicker.adapter

import android.net.Uri
import java.util.concurrent.TimeUnit

data class SelectableMedia constructor(
    val id: Int,
    val type: Type,
    val uri: Uri,
    val displayName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Long? = null,  // milliseconds
    val dateAdded: Long? = null,  // milliseconds
    val dateModified: Long? = null,  // milliseconds
    val dateTaken: Long? = null,  // milliseconds
    val size: Long? = null,
    val selected: Boolean = false
) {

    companion object {
        fun fromCamera(type: Type, uri: Uri): SelectableMedia {
            val now = System.currentTimeMillis()
            return SelectableMedia(
                id = -1,
                type = type,
                uri = uri,
                displayName = uri.lastPathSegment,
                width = null,
                height = null,
                duration = null,
                dateAdded = now,
                dateModified = now,
                dateTaken = now,
                size = null,
                selected = false
            )
        }
    }

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