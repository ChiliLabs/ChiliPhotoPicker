package lv.chi.photopicker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.view_pickable_media_item.view.*
import lv.chi.photopicker.R
import lv.chi.photopicker.loader.ImageLoader

internal class MediaPickerAdapter(
    private val onMediaItemClicked: (SelectableMedia) -> Unit,
    private val multiple: Boolean,
    private val imageLoader: ImageLoader
) : ListAdapter<SelectableMedia, MediaPickerAdapter.VideoPickerViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        VideoPickerViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.view_pickable_media_item, parent, false)
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

    inner class VideoPickerViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    companion object {
        private const val SELECTED_PAYLOAD = "selected_payload"

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