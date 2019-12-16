package lv.chi.photopicker

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_photo_picker.*
import kotlinx.android.synthetic.main.fragment_photo_picker.view.*
import kotlinx.android.synthetic.main.view_grant_permission.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import lv.chi.photopicker.PickerViewModel.Companion.SELECTION_UNDEFINED
import lv.chi.photopicker.adapter.ImagePickerAdapter
import lv.chi.photopicker.adapter.SelectableImage
import lv.chi.photopicker.ext.Intents
import lv.chi.photopicker.ext.isPermissionGranted
import lv.chi.photopicker.ext.parentAs
import lv.chi.photopicker.utils.CameraActivity
import lv.chi.photopicker.utils.NonDismissibleBehavior
import lv.chi.photopicker.utils.SpacingItemDecoration

class PhotoPickerFragment : DialogFragment() {

    private lateinit var photoAdapter: ImagePickerAdapter

    private lateinit var vm: PickerViewModel

    private var behavior: BottomSheetBehavior<FrameLayout>? = null

    private var snackBar: Snackbar? = null

    private val cornerRadiusOutValue = TypedValue()

    private lateinit var contextWrapper: ContextThemeWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ViewModelProviders.of(this).get(PickerViewModel::class.java)
        vm.setMaxSelectionCount(getMaxSelection(requireArguments()))

        contextWrapper = ContextThemeWrapper(context, getTheme(requireArguments()))

        photoAdapter = ImagePickerAdapter(
            onImageClick = ::onImageClicked,
            multiple = getAllowMultiple(requireArguments()),
            imageLoader = PickerConfiguration.getImageLoader()
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return PickerDialog(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        LayoutInflater.from(contextWrapper).inflate(
            R.layout.fragment_photo_picker,
            container,
            false
        )
            .apply {
                contextWrapper.theme.resolveAttribute(R.attr.pickerCornerRadius, cornerRadiusOutValue, true)

                photos.apply {
                    adapter = photoAdapter
                    val margin = context.resources.getDimensionPixelSize(R.dimen.margin_2dp)
                    addItemDecoration(SpacingItemDecoration(margin, margin, margin, margin))
                    layoutManager = GridLayoutManager(
                        requireContext(),
                        if (orientation() == Configuration.ORIENTATION_LANDSCAPE) 5 else 3,
                        RecyclerView.VERTICAL,
                        false
                    )
                }

                camera_container.isVisible = getAllowCamera(requireArguments())
                gallery_container.setOnClickListener { pickImageGallery() }
                camera_container.setOnClickListener { pickImageCamera() }
                findViewById<TextView>(R.id.grant).setOnClickListener { grantPermissions() }

                pickerBottomSheetCallback.setMargin(requireContext().resources.getDimensionPixelSize(cornerRadiusOutValue.resourceId))
            }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        coordinator.doOnLayout {
            behavior = BottomSheetBehavior.from<FrameLayout>(design_bottom_sheet).apply {
                addBottomSheetCallback(pickerBottomSheetCallback)
                isHideable = true
                skipCollapsed = false
                peekHeight =
                    if (orientation() == Configuration.ORIENTATION_LANDSCAPE) it.measuredHeight / 2
                    else BottomSheetBehavior.PEEK_HEIGHT_AUTO
            }
        }

        if (savedInstanceState == null) updateState()

        vm.hasPermission.observe(viewLifecycleOwner, Observer { handlePermission(it) })
        vm.selected.observe(viewLifecycleOwner, Observer { handleSelected(it) })
        vm.photos.observe(viewLifecycleOwner, Observer { handlePhotos(it) })
        vm.inProgress.observe(viewLifecycleOwner, Observer {
            photos.visibility = if (it) View.INVISIBLE else View.VISIBLE
            progressbar.visibility = if (it) View.VISIBLE else View.GONE
        })
        vm.hasContent.observe(viewLifecycleOwner, Observer {
            pickerBottomSheetCallback.setNeedTransformation(it)
            if (it) remeasureContentDialog()
        })
        vm.maxSelectionReached.observe(viewLifecycleOwner, Observer {
            val max = getMaxSelection(requireArguments())
            Toast.makeText(
                requireContext(),
                resources.getQuantityString((R.plurals.picker_max_selection_reached), max, max),
                Toast.LENGTH_SHORT
            ).show()
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
        if (getAllowMultiple(requireArguments())) {
            vm.toggleSelected(state)
        } else {
            parentAs<Callback>()?.onImagesPicked(arrayListOf(state.uri))
            dismiss()
        }
    }

    private fun remeasureContentDialog() {
        coordinator.doOnLayout {
            val heightLp = design_bottom_sheet.layoutParams
            heightLp.height = coordinator.measuredHeight + requireContext().resources.getDimensionPixelSize(cornerRadiusOutValue.resourceId)
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
                val view = LayoutInflater.from(contextWrapper).inflate(R.layout.view_snackbar, null)
                snackBar = Snackbar.make(coordinator, "", Snackbar.LENGTH_INDEFINITE)
                    .setBehavior(NonDismissibleBehavior())
                (snackBar?.view as? ViewGroup)?.apply {
                    setPadding(0, 0, 0, 0)
                    removeAllViews()
                    addView(view)
                    findViewById<ImageView>(R.id.cancel).setOnClickListener { vm.clearSelected() }
                    findViewById<TextView>(R.id.select).setOnClickListener { uploadSelected() }
                }
                snackBar?.show()
            }
            snackBar?.view?.findViewById<TextView>(R.id.count)?.text =
                resources.getQuantityString(R.plurals.picker_selected_count, count, count)
        }
    }

    private fun handlePhotos(photos: List<SelectableImage>) {
        vm.setInProgress(false)
        photoAdapter.submitList(photos.toMutableList())
        empty_text.visibility =
            if (photos.isEmpty() && vm.hasPermission.value == true) View.VISIBLE
            else View.GONE
    }

    private fun loadPhotos() {
        vm.setInProgress(true)
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
                ).let { vm.setPhotos(it)}
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
            vm.setHasPermission(true)
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
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, getAllowMultiple(requireArguments()))
        }

        startActivityForResult(
            Intent.createChooser(intent, getString(R.string.picker_select_photo)),
            Request.ADD_PHOTO_GALLERY
        )
    }

    private fun uploadSelected() {
        val selected = ArrayList(vm.selected.value ?: emptyList())

        parentAs<Callback>()?.onImagesPicked(selected)
        dismiss()
    }

    private fun orientation() = requireContext().resources.configuration.orientation

    private object Request {
        const val MEDIA_ACCESS_PERMISSION = 1
        const val ADD_PHOTO_CAMERA = 2
        const val ADD_PHOTO_GALLERY = 3
    }

    interface Callback {
        fun onImagesPicked(photos: ArrayList<Uri>)
    }

    companion object {
        private const val KEY_MULTIPLE = "KEY_MULTIPLE"
        private const val KEY_ALLOW_CAMERA = "KEY_ALLOW_CAMERA"
        private const val KEY_THEME = "KEY_THEME"
        private const val KEY_MAX_SELECTION = "KEY_MAX_SELECTION"

        fun newInstance(
            multiple: Boolean = false,
            allowCamera: Boolean = false,
            maxSelection: Int = SELECTION_UNDEFINED,
            @StyleRes theme: Int = R.style.ChiliPhotoPicker_Light
        ) = PhotoPickerFragment().apply {
            arguments = bundleOf(
                KEY_MULTIPLE to multiple,
                KEY_ALLOW_CAMERA to allowCamera,
                KEY_MAX_SELECTION to maxSelection,
                KEY_THEME to theme
            )
        }

        private fun getTheme(args: Bundle) = args.getInt(KEY_THEME)
        private fun getAllowCamera(args: Bundle) = args.getBoolean(KEY_ALLOW_CAMERA)
        private fun getAllowMultiple(args: Bundle) = args.getBoolean(KEY_MULTIPLE)
        private fun getMaxSelection(args: Bundle) = args.getInt(KEY_MAX_SELECTION)
    }
}