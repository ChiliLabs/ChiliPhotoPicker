package lv.chi.photopicker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView
import lv.chi.photopicker.R
import lv.chi.photopicker.loader.ImageLoader

internal class MediaPickerAdapter(
    private val onMediaClick: (SelectableMedia) -> Unit,
    private val multiple: Boolean,
    private val imageLoader: ImageLoader
) : RecyclerView.Adapter<MediaPickerAdapter.MediaPickerViewHolder>() {

    companion object {
        private val TAG = MediaPickerAdapter::class.java.simpleName

        private const val SELECTED_PAYLOAD = "selected_payload"

        private const val VIEW_TYPE_IMAGE = 100
        private const val VIEW_TYPE_VIDEO = 101

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

    private val asyncListDiffer: AsyncListDiffer<SelectableMedia> by lazy {
        AsyncListDiffer(this, diffCallback)
    }

    fun submitList(list: List<SelectableMedia>) {
        asyncListDiffer.submitList(list)
    }

    override fun getItemCount(): Int = asyncListDiffer.currentList.size

    private fun getItem(position: Int): SelectableMedia = asyncListDiffer.currentList[position]

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).type == SelectableMedia.Type.VIDEO) {
            VIEW_TYPE_VIDEO
        } else {
            VIEW_TYPE_IMAGE
        }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): MediaPickerViewHolder {
        val holder = when (type) {
            VIEW_TYPE_IMAGE -> {
                ImagePickerViewHolder(
                    LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.view_pickable_image, parent, false)
                )
            }
            VIEW_TYPE_VIDEO -> {
                VideoPickerViewHolder(
                    LayoutInflater
                        .from(parent.context)
                        .inflate(R.layout.view_pickable_video, parent, false)
                )
            }
            else ->
                throw IllegalStateException("Something wrong happened. There is no ViewHolder for this viewType.")
        }
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

    override fun onBindViewHolder(holder: MediaPickerViewHolder, position: Int) = with(holder) {
        val item = getItem(position)

        imageLoader.loadImage(itemView.context, holder.imageView, item.uri)

        holder.checkBox.isChecked = item.selected

        if (getItemViewType(position) == VIEW_TYPE_VIDEO && holder is VideoPickerViewHolder) {
            if (item.duration == null) {
                holder.durationView.setText(R.string.picker_video)
            } else {
                holder.durationView.text = item.getDuration()
            }
        }

        itemView.setOnClickListener { onMediaClick(getItem(position)) }
    }

    open class MediaPickerViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ShapeableImageView = itemView.findViewById(R.id.imageView)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBox)
    }

    class ImagePickerViewHolder(view: View) : MediaPickerViewHolder(view)

    class VideoPickerViewHolder(view: View) : MediaPickerViewHolder(view) {
        val durationView: MaterialTextView = itemView.findViewById(R.id.durationView)
    }

}