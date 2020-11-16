package lv.chi.photopicker

import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lv.chi.photopicker.adapter.SelectableMedia
import lv.chi.photopicker.utils.SingleLiveEvent

internal class MediaPickerViewModel : ViewModel() {

    companion object {
        const val SELECTION_UNDEFINED = -1
    }

    private val hasContentData = MutableLiveData(false)
    private val inProgressData = MutableLiveData(false)
    private val hasPermissionData = MutableLiveData(false)
    private val selectedData = MutableLiveData<ArrayList<Uri>>(arrayListOf())
    private val mediaData = MutableLiveData<ArrayList<SelectableMedia>>(arrayListOf())
    private val maxSelectionReachedData = SingleLiveEvent<Unit>()

    private var maxSelectionCount = SELECTION_UNDEFINED

    val hasContent: LiveData<Boolean> = Transformations.distinctUntilChanged(hasContentData)
    val inProgress: LiveData<Boolean> = inProgressData
    val hasPermission: LiveData<Boolean> = hasPermissionData
    val selected: LiveData<ArrayList<Uri>> = selectedData
    val media: LiveData<ArrayList<SelectableMedia>> = mediaData
    val maxSelectionReached: LiveData<Unit> = maxSelectionReachedData

    fun setHasPermission(hasPermission: Boolean) = hasPermissionData.postValue(hasPermission)

    fun setMaxSelectionCount(count: Int) {
        maxSelectionCount = count
    }

    fun clearSelected() {
        viewModelScope.launch {
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
        viewModelScope.launch(Dispatchers.IO) {
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
                ?.let { position -> media[position] = selectableMedia.copy(selected = !selectableMedia.selected) }

            selectedData.postValue(selected)
            mediaData.postValue(media)
        }
    }

    private fun canSelectMore(size: Int) = maxSelectionCount == SELECTION_UNDEFINED || maxSelectionCount > size

    private fun readValueAtCursor(cursor: Cursor): SelectableMedia {
        val id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
        val uri = "file://${cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))}"
        return SelectableMedia(id, Uri.parse(uri), false)
    }

}