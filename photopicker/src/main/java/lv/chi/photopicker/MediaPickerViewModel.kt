package lv.chi.photopicker

import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.*
import kotlinx.coroutines.*
import lv.chi.photopicker.adapter.SelectableMedia
import lv.chi.photopicker.utils.SingleLiveEvent
import kotlin.collections.ArrayList

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
    private val selectedData = MutableLiveData<ArrayList<Uri>>(arrayListOf())
    private val mediaData = MutableLiveData<ArrayList<SelectableMedia>>()
    private val maxSelectionReachedData = SingleLiveEvent<Unit>()

    private var maxSelectionCount = SELECTION_UNDEFINED

    fun getHasContent(): LiveData<Boolean> = Transformations.distinctUntilChanged(hasContentData)
    fun getInProgress(): LiveData<Boolean> = inProgressData
    fun getHasPermission(): LiveData<Boolean> = hasPermissionData
    fun getSelected(): LiveData<ArrayList<Uri>> = selectedData
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
                selectableMedia.selected -> selected.remove(selectableMedia.uri)
                canSelectMore(selected.size) -> selected.add(selectableMedia.uri)
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
        val id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))

        val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
        val type = if (mimeType.contains("video")) {
            SelectableMedia.Type.VIDEO
        } else SelectableMedia.Type.IMAGE

        val uri = "file://${cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))}"

        val duration = if (type == SelectableMedia.Type.VIDEO) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION))
            } else {
                MediaMetadataRetriever().use {
                    it.setDataSource(uri)

                    return@use it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        .toLong()
                }
            }
        } else {
            null
        }

        return SelectableMedia(id, type, Uri.parse(uri), false, duration)
    }

}