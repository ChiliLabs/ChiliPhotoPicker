package lv.chi.photopicker

import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lv.chi.photopicker.adapter.SelectableMedia
import lv.chi.photopicker.utils.SingleLiveEvent
import java.util.concurrent.TimeUnit

internal class MediaPickerViewModel : ViewModel() {

    companion object {
        private val TAG = MediaPickerViewModel::class.java.simpleName

        const val SELECTION_UNDEFINED = -1
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        error.printStackTrace()
    }

    private val hasContentData = MutableLiveData(false)
    private val inProgressData = MutableLiveData(true)
    private val hasPermissionData = MutableLiveData(false)
    private val selectedData = MutableLiveData<ArrayList<SelectableMedia>>(arrayListOf())
    private val mediaData = MutableLiveData<ArrayList<SelectableMedia>>()
    private val maxSelectionReachedData = SingleLiveEvent<Unit>()

    private var maxSelectionCount = SELECTION_UNDEFINED

    fun getHasContent(): LiveData<Boolean> = Transformations.distinctUntilChanged(hasContentData)
    fun getInProgress(): LiveData<Boolean> = inProgressData
    fun getHasPermission(): LiveData<Boolean> = hasPermissionData
    fun getSelected(): LiveData<ArrayList<SelectableMedia>> = selectedData
    fun getMedia(): LiveData<ArrayList<SelectableMedia>> = mediaData
    fun getMaxSelectionReached(): LiveData<Unit> = maxSelectionReachedData

    fun setHasPermission(hasPermission: Boolean) {
        hasPermissionData.postValue(hasPermission)
    }

    fun setMaxSelectionCount(count: Int) {
        maxSelectionCount = count
    }

    fun clearSelected() {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val media = requireNotNull(mediaData.value).map { it.copy(selected = false) }
            val array = arrayListOf<SelectableMedia>()
            array.addAll(media)
            mediaData.postValue(array)
            selectedData.postValue(arrayListOf())
        }
    }

    fun setMedia(cursor: Cursor?) {
        cursor?.let { c ->
            val array = arrayListOf<SelectableMedia>()
            array.addAll(
                generateSequence { if (c.moveToNext()) c else null }
                    .map { readValueAtCursor(cursor) }
                    .toList()
            )
            hasContentData.postValue(array.isNotEmpty())
            mediaData.postValue(array)
        }
    }

    fun setInProgress(progress: Boolean) {
        inProgressData.postValue(progress)
    }

    fun toggleSelected(selectableMedia: SelectableMedia) {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val selected = requireNotNull(selectedData.value)

            when {
                selectableMedia.selected -> selected.removeAll { it.id == selectableMedia.id }
                canSelectMore(selected.size) -> selected.add(selectableMedia)
                else -> {
                    maxSelectionReachedData.postValue(Unit)
                    return@launch
                }
            }

            val media = requireNotNull(mediaData.value)
            media.indexOfFirst { item -> item.id == selectableMedia.id }
                .takeIf { position -> position != -1 }
                ?.let { position ->
                    media[position] = selectableMedia.copy(selected = !selectableMedia.selected)
                }

            selectedData.postValue(selected)
            mediaData.postValue(media)
        }
    }

    private fun canSelectMore(size: Int): Boolean =
            maxSelectionCount == SELECTION_UNDEFINED || maxSelectionCount > size

    private fun readValueAtCursor(cursor: Cursor): SelectableMedia {
        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))

        val type = if (mimeType.contains("video", ignoreCase = true)) {
            SelectableMedia.Type.VIDEO
        } else {
            SelectableMedia.Type.IMAGE
        }

        return when (type) {
            SelectableMedia.Type.IMAGE -> {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID))
                val uri = "file://${cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATA))}"
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DISPLAY_NAME))
                val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.WIDTH))
                val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.HEIGHT))

                var dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_ADDED))
                dateAdded = TimeUnit.SECONDS.toMillis(dateAdded)

                var dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_MODIFIED))
                dateModified = TimeUnit.SECONDS.toMillis(dateModified)

                val dateTaken = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_TAKEN))
                } else {
                    null
                }

                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.SIZE))

                SelectableMedia(
                    id = id,
                    type = type,
                    uri = Uri.parse(uri),
                    displayName = displayName,
                    width = width,
                    height = height,
                    dateAdded = dateAdded,
                    dateModified = dateModified,
                    dateTaken = dateTaken,
                    size = size,
                    selected = false
                )
            }
            SelectableMedia.Type.VIDEO -> {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns._ID))
                val uri = "file://${cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATA))}"
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DISPLAY_NAME))
                val width = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.WIDTH))
                val height = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.HEIGHT))

                val duration = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION))
                } else {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(uri)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
                    retriever.close()
                    duration
                }

                var dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATE_ADDED))
                dateAdded = TimeUnit.SECONDS.toMillis(dateAdded)

                var dateModified = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATE_MODIFIED))
                dateModified = TimeUnit.SECONDS.toMillis(dateModified)

                val dateTaken = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DATE_TAKEN))
                } else {
                    null
                }

                val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.SIZE))

                SelectableMedia(
                    id = id,
                    type = type,
                    uri = Uri.parse(uri),
                    displayName = displayName,
                    width = width,
                    height = height,
                    duration = duration,
                    dateAdded = dateAdded,
                    dateModified = dateModified,
                    dateTaken = dateTaken,
                    size = size,
                    selected = false
                )
            }
        }
    }

}