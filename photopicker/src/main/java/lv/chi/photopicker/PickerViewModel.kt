package lv.chi.photopicker

import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import lv.chi.photopicker.adapter.SelectableMedia
import lv.chi.photopicker.utils.SingleLiveEvent

internal class PickerViewModel : ViewModel() {

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
    val mediaItems: LiveData<ArrayList<SelectableMedia>> = mediaData
    val maxSelectionReached: LiveData<Unit> = maxSelectionReachedData

    fun setHasPermission(hasPermission: Boolean) = hasPermissionData.postValue(hasPermission)

    fun setMaxSelectionCount(count: Int) {
        maxSelectionCount = count
    }

    fun clearSelected() {
        GlobalScope.launch {
            val mediaItems = requireNotNull(mediaData.value).map { it.copy(selected = false) }
            val array = arrayListOf<SelectableMedia>()
            array.addAll(mediaItems)
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

    fun toggleSelected(media: SelectableMedia) {
        GlobalScope.launch(Dispatchers.IO) {
            val selected = requireNotNull(selectedData.value)

            when {
                media.selected -> selected.remove(media.uri)
                canSelectMore(selected.size) -> selected.add(media.uri)
                else -> {
                    maxSelectionReachedData.postValue(Unit)
                    return@launch
                }
            }

            val mediaItems = requireNotNull(mediaData.value)
            mediaItems.indexOfFirst { item -> item.id == media.id }
                .takeIf { pos -> pos != -1 }
                ?.let { pos -> mediaItems[pos] = media.copy(selected = !media.selected) }

            selectedData.postValue(selected)
            mediaData.postValue(mediaItems)

        }
    }

    private fun canSelectMore(size: Int) = maxSelectionCount == SELECTION_UNDEFINED || maxSelectionCount > size

    private fun readValueAtCursor(cursor: Cursor): SelectableMedia {
        val id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
        val uri = "file://${cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))}"
        return SelectableMedia(id, Uri.parse(uri), false)
    }

    companion object {
        const val SELECTION_UNDEFINED = -1
    }
}