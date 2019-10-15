package lv.chi.photopicker

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.doOnLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_photo_picker.*
import kotlinx.android.synthetic.main.fragment_photo_picker.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import lv.chi.photopicker.adapter.ImagePickerAdapter
import lv.chi.photopicker.adapter.SelectableImage
import lv.chi.photopicker.ext.Intents
import lv.chi.photopicker.ext.isPermissionGranted
import lv.chi.photopicker.ext.parentAs
import lv.chi.photopicker.loader.ImageLoader
import lv.chi.photopicker.utils.CameraActivity
import lv.chi.photopicker.utils.FileProviders
import lv.chi.photopicker.utils.NonDismissibleBehavior
import lv.chi.photopicker.utils.SpacingItemDecoration

class ImagePickerFragment : DialogFragment() {

    private lateinit var photoAdapter: ImagePickerAdapter

    private val pickerState = PickerState()

    private var multiple: Boolean = false

    private var behavior: BottomSheetBehavior<FrameLayout>? = null

    private var snackBar: Snackbar? = null

    private lateinit var imageLoader: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        multiple = requireArguments().getBoolean(Key.MULTIPLE)
        photoAdapter = ImagePickerAdapter(::onImageClicked, multiple, imageLoader)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return PickerDialog(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        LayoutInflater.from(requireContext()).inflate(
            R.layout.fragment_photo_picker,
            container,
            false
        )
            .apply {
                photos.apply {
                    adapter = photoAdapter
                    val margin = context.resources.getDimensionPixelSize(R.dimen.margin_2dp)
                    addItemDecoration(SpacingItemDecoration(margin, margin, margin, margin))
                }

                gallery_container.setOnClickListener { pickImageGallery() }
                camera_container.setOnClickListener { pickImageCamera() }
                grant.setOnClickListener { grantPermissions() }

                pickerBottomSheetCallback.setMargin(requireContext().resources.getDimensionPixelSize(R.dimen.picker_expanded_margin))

                behavior = BottomSheetBehavior.from<FrameLayout>(design_bottom_sheet).apply {
                    addBottomSheetCallback(pickerBottomSheetCallback)
                    isHideable = true
                    skipCollapsed = false
                    peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
                }
            }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateState()

        pickerState.hasPermission.observe(this, Observer { handlePermission(it) })
        pickerState.selected.observe(this, Observer { handleSelected(it) })
        pickerState.photos.observe(this, Observer { handlePhotos(it) })
        pickerState.inProgress.observe(this, Observer {
            photos.visibility = if (it) View.INVISIBLE else View.VISIBLE
            progressbar.visibility = if (it) View.VISIBLE else View.GONE
        })
        pickerState.hasContent.observe(this, Observer {
            pickerBottomSheetCallback.setNeedTransformation(it)
            if (it) remeasureContentDialog()
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Request.MEDIA_ACCESS_PERMISSION && isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE))
            updateState()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            Request.ADD_PHOTO_GALLERY, Request.ADD_PHOTO_CAMERA -> {
                if (resultCode == Activity.RESULT_OK) {
                    Intents.getUriResult(data)?.let {
                        parentAs<Callback>()?.onImagesPicked(it)
                        dismiss()
                    }
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun onImageClicked(state: SelectableImage) {
        if (multiple) pickerState.toggleSelected(state)
        else {
            parentAs<Callback>()?.onImagesPicked(arrayListOf(state.uri))
            dismiss()
        }
    }

    fun imageLoader(imageLoader: ImageLoader): ImagePickerFragment {
        this.imageLoader = imageLoader
        return this
    }

    fun authority(authority: String): ImagePickerFragment {
        FileProviders.authority = authority
        return this
    }

    private fun remeasureContentDialog() {
        coordinator.doOnLayout {
            val heightLp = design_bottom_sheet.layoutParams
            heightLp.height = coordinator.measuredHeight + requireContext().resources.getDimensionPixelSize(R.dimen.picker_expanded_margin)
            design_bottom_sheet.layoutParams = heightLp
        }
    }

    private fun handlePermission(hasPermission: Boolean) {
        permission_text.visibility = if (hasPermission) View.GONE else View.VISIBLE
        grant.visibility = if (hasPermission) View.GONE else View.VISIBLE

        photos.visibility = if (hasPermission) View.VISIBLE else View.INVISIBLE
    }

    private fun handleSelected(selected: List<Uri>) {
        if (selected.isEmpty()) {
            snackBar?.dismiss()
            snackBar = null
        } else {
            val count = selected.count()
            if (snackBar == null) {
                val view = LayoutInflater.from(requireContext()).inflate(R.layout.view_snackbar, null)
                snackBar = Snackbar.make(coordinator, "", Snackbar.LENGTH_INDEFINITE)
                    .setBehavior(NonDismissibleBehavior())
                (snackBar?.view as? ViewGroup)?.apply {
                    setPadding(0, 0, 0, 0)
                    removeAllViews()
                    addView(view)
                    findViewById<ImageView>(R.id.cancel).setOnClickListener { pickerState.clearSelected() }
                    findViewById<Button>(R.id.upload).setOnClickListener { uploadSelected() }
                }
                snackBar?.show()
            }
            snackBar?.view?.findViewById<TextView>(R.id.count)?.text =
                resources.getQuantityString(R.plurals.picker_selected_count, count, count)
        }
    }

    private fun handlePhotos(photos: List<SelectableImage>) {
        pickerState.setInProgress(false)
        photoAdapter.submitList(photos.toMutableList())
        empty_text.visibility =
            if (photos.isEmpty() && pickerState.hasPermission.value == true) View.VISIBLE
            else View.GONE
    }

    private fun loadPhotos() {
        pickerState.setInProgress(true)
        GlobalScope.launch(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
            )

            val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            requireContext()
                .contentResolver
                .query(
                    images,
                    projection,
                    null,
                    null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
                ).let { pickerState.setPhotos(it)}
        }
    }

    private fun grantPermissions() {
        if (!isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE))
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                Request.MEDIA_ACCESS_PERMISSION
            )
    }

    private fun updateState() {
        if (isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            pickerState.setHasPermission(true)
            loadPhotos()
        }
    }

    private val pickerBottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        private var margin = 0
        private var needTransformation = false
        override fun onStateChanged(bottomSheet: View, @BottomSheetBehavior.State newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                dismiss()
            }
        }
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            if (!needTransformation) return
            val calculatedSpacing = calculateSpacing(slideOffset)
            design_bottom_sheet.translationY = -calculatedSpacing
            design_bottom_sheet.setPadding(0, calculatedSpacing.toInt(), 0, 0)
        }

        fun setMargin(margin: Int) {
            this.margin = margin
        }

        fun setNeedTransformation(need: Boolean) {
            needTransformation = need
        }

        private fun calculateSpacing(progress: Float) = margin * progress
    }

    private fun pickImageCamera() {
        startActivityForResult(CameraActivity.createIntent(requireContext()), Request.ADD_PHOTO_CAMERA)
    }

    private fun pickImageGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        }

        startActivityForResult(
            Intent.createChooser(intent, getString(R.string.picker_select_photo)),
            Request.ADD_PHOTO_GALLERY
        )
    }

    private fun uploadSelected() {
        val selected = ArrayList(pickerState.selected.value ?: emptyList())

        parentAs<Callback>()?.onImagesPicked(selected)
        dismiss()
    }

    private object Request {
        const val MEDIA_ACCESS_PERMISSION = 1
        const val ADD_PHOTO_CAMERA = 2
        const val ADD_PHOTO_GALLERY = 3
    }

    private object Key {
        val MULTIPLE = "multiple"
    }

    interface Callback {
        fun onImagesPicked(photos: ArrayList<Uri>)
    }

    companion object {
        fun newInstance(multiple: Boolean) = ImagePickerFragment().apply {
            arguments = Bundle().apply {
                putBoolean(Key.MULTIPLE, multiple)
            }
        }
    }
}