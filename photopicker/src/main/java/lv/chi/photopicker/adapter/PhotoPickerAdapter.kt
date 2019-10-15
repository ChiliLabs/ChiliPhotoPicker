package lv.chi.photopicker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.view_pickable_image.view.*
import lv.chi.photopicker.R
import lv.chi.photopicker.loader.ImageLoader

internal class ImagePickerAdapter(
    private val onImageClick: (SelectableImage) -> Unit,
    private val multiple: Boolean,
    private val imageLoader: ImageLoader
) : ListAdapter<SelectableImage, ImagePickerAdapter.ImagePickerViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        ImagePickerViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.view_pickable_image, parent, false)
                .apply { checkbox.visibility = if (multiple) View.VISIBLE else View.GONE }
        )

    override fun onBindViewHolder(
        holder: ImagePickerViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val item = getItem(position)
        holder.view.apply {
            imageLoader.loadImage(context, photo_item, item.uri)
            setOnClickListener { onImageClick(item) }
            checkbox.isChecked = item.selected
        }
    }

    override fun onBindViewHolder(holder: ImagePickerViewHolder, position: Int) {
        // No-op
    }

    class ImagePickerViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    companion object {
        private const val SELECTED_PAYLOAD = "selected_payload"

        private val DiffCallback = object : DiffUtil.ItemCallback<SelectableImage>() {
            override fun areItemsTheSame(
                oldItem: SelectableImage,
                newItem: SelectableImage
            ): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: SelectableImage,
                newItem: SelectableImage
            ): Boolean = oldItem == newItem

            override fun getChangePayload(
                oldItem: SelectableImage,
                newItem: SelectableImage
            ): Any? = when {
                oldItem.selected != newItem.selected -> SELECTED_PAYLOAD
                else -> null
            }
        }
    }
}