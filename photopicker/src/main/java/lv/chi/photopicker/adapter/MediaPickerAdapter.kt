package lv.chi.photopicker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.view_pickable_item_photo.view.*
import lv.chi.photopicker.R
import lv.chi.photopicker.loader.ImageLoader
import java.util.*

internal class MediaPickerAdapter(
    private val onMediaItemClicked: (SelectableMedia) -> Unit,
    private val multiple: Boolean,
    private val imageLoader: ImageLoader
) : ListAdapter<SelectableMedia, MediaPickerAdapter.VideoPickerViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        VideoPickerViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(
                    if (type == VIEW_TYPE_PHOTO) R.layout.view_pickable_item_photo else R.layout.view_pickable_item_video,
                    parent,
                    false
                )
                .apply { checkbox.visibility = if (multiple) View.VISIBLE else View.GONE }
        )

    override fun onBindViewHolder(
        holder: VideoPickerViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        payloads.firstOrNull()?.let {
            holder.view.checkbox.isChecked = getItem(position).selected
        } ?: super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: VideoPickerViewHolder, position: Int) {
        val item = getItem(position)
        holder.view.apply {
            imageLoader.loadImage(context, media_item, item.uri)
            setOnClickListener { onMediaItemClicked(getItem(position)) }
            checkbox.isChecked = item.selected
        }
    }

    override fun getItemViewType(position: Int): Int {
        val itemAtPosition = getItem(position)
        val extension = MimeTypeMap.getFileExtensionFromUrl(itemAtPosition.uri.toString())
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            extension.toLowerCase(
                Locale.ROOT
            )
        )

        mimeType?.let {
            return if (it.contains("video"))
                VIEW_TYPE_VIDEO
            else
                VIEW_TYPE_PHOTO
        } ?: return VIEW_TYPE_PHOTO
    }

    inner class VideoPickerViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    companion object {
        private const val SELECTED_PAYLOAD = "selected_payload"
        private const val VIEW_TYPE_VIDEO = 8738
        private const val VIEW_TYPE_PHOTO = 2837

        val DiffCallback = object : DiffUtil.ItemCallback<SelectableMedia>() {
            override fun areItemsTheSame(
                oldItem: SelectableMedia,
                newItem: SelectableMedia
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: SelectableMedia,
                newItem: SelectableMedia
            ): Boolean = oldItem == newItem

            override fun getChangePayload(
                oldItem: SelectableMedia,
                newItem: SelectableMedia
            ): Any? = when {
                oldItem.selected != newItem.selected -> SELECTED_PAYLOAD
                else -> null
            }
        }
    }
}