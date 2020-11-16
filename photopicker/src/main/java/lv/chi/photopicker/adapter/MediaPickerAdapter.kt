package lv.chi.photopicker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import lv.chi.photopicker.R
import lv.chi.photopicker.loader.ImageLoader

internal class MediaPickerAdapter(
    private val onMediaClick: (SelectableMedia) -> Unit,
    private val multiple: Boolean,
    private val imageLoader: ImageLoader
) : ListAdapter<SelectableMedia, MediaPickerAdapter.MediaPickerViewHolder>(diffCallback) {

    companion object {
        private const val SELECTED_PAYLOAD = "selected_payload"

        private val diffCallback = object : DiffUtil.ItemCallback<SelectableMedia>() {
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

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): MediaPickerViewHolder {
        val holder = MediaPickerViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.view_pickable_image, parent, false)
        )
        holder.checkBox.visibility = if (multiple) View.VISIBLE else View.GONE
        return holder
    }

    override fun onBindViewHolder(
        holder: MediaPickerViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        payloads.firstOrNull()?.let {
            holder.checkBox.isChecked = getItem(position).selected
        } ?: super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: MediaPickerViewHolder, position: Int) {
        val item = getItem(position)
        holder.view.apply {
            imageLoader.loadImage(context, holder.imageView, item.uri)
            setOnClickListener { onMediaClick(getItem(position)) }
            holder.checkBox.isChecked = item.selected
        }
    }

    class MediaPickerViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.image_view)
        val checkBox: CheckBox = view.findViewById(R.id.check_box)
    }

}