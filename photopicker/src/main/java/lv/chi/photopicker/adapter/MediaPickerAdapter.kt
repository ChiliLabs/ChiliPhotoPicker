package lv.chi.photopicker.adapter

import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lv.chi.photopicker.R
import lv.chi.photopicker.loader.ImageLoader
import java.util.*
import java.util.concurrent.TimeUnit

internal class MediaPickerAdapter(
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onMediaClick: (SelectableMedia) -> Unit,
    private val multiple: Boolean,
    private val imageLoader: ImageLoader
) : ListAdapter<SelectableMedia, MediaPickerAdapter.MediaPickerViewHolder>(diffCallback) {

    companion object {
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

    override fun getItemViewType(position: Int): Int {
        val itemAtPosition = getItem(position)
        val extension = MimeTypeMap.getFileExtensionFromUrl(itemAtPosition.uri.toString())
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension.toLowerCase(Locale.ROOT))

        mimeType?.let {
            return if (it.contains("video")) VIEW_TYPE_VIDEO
            else VIEW_TYPE_IMAGE
        } ?: return VIEW_TYPE_IMAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): MediaPickerViewHolder {
        val holder = if (type == VIEW_TYPE_VIDEO) {
            VideoPickerViewHolder(
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.view_pickable_video, parent, false)
            )
        } else {
            ImagePickerViewHolder(
                LayoutInflater
                    .from(parent.context)
                    .inflate(R.layout.view_pickable_image, parent, false)
            )
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

    override fun onBindViewHolder(holder: MediaPickerViewHolder, position: Int) {
        val item = getItem(position)
        holder.view.apply {
            imageLoader.loadImage(context, holder.imageView, item.uri)
            setOnClickListener { onMediaClick(getItem(position)) }
            holder.checkBox.isChecked = item.selected

            if (getItemViewType(position) == VIEW_TYPE_VIDEO && holder is VideoPickerViewHolder) {
                val video = getItem(position)

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        MediaMetadataRetriever().apply {
                            setDataSource(context, video.uri)

                            val videoLength =
                                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()

                            withContext(Dispatchers.Main) {
                                holder.durationView.text = getDurationString(videoLength)
                            }

                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                close()
                            } else {
                                release()
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    open class MediaPickerViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ShapeableImageView = view.findViewById(R.id.imageView)
        val checkBox: MaterialCheckBox = view.findViewById(R.id.checkBox)
    }

    class ImagePickerViewHolder(view: View) : MediaPickerViewHolder(view)

    class VideoPickerViewHolder(view: View) : MediaPickerViewHolder(view) {
        val durationView: MaterialTextView = view.findViewById(R.id.durationView)
    }

    private fun getDurationString(duration: Long): String {
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