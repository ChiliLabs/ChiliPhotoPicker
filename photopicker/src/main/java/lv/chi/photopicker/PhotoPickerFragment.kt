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
import android.widget.*
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lv.chi.photopicker.PickerViewModel.Companion.SELECTION_UNDEFINED
import lv.chi.photopicker.adapter.ImagePickerAdapter
import lv.chi.photopicker.adapter.SelectableImage
import lv.chi.photopicker.ext.*
import lv.chi.photopicker.ext.Intents
import lv.chi.photopicker.ext.isPermissionGranted
import lv.chi.photopicker.ext.parentAs
import lv.chi.photopicker.utils.CameraActivity
import lv.chi.photopicker.utils.NonDismissibleBehavior
import lv.chi.photopicker.utils.SpacingItemDecoration

class PhotoPickerFragment : DialogFragment() {

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
            arguments = Bundle().apply {
                putBoolean(KEY_MULTIPLE, multiple)
                putBoolean(KEY_ALLOW_CAMERA, allowCamera)
                putInt(KEY_MAX_SELECTION, maxSelection)
                putInt(KEY_THEME, theme)
            }
        }

        private fun getTheme(args: Bundle) = args.getInt(KEY_THEME)
        private fun getAllowCamera(args: Bundle) = args.getBoolean(KEY_ALLOW_CAMERA)
        private fun getAllowMultiple(args: Bundle) = args.getBoolean(KEY_MULTIPLE)
        private fun getMaxSelection(args: Bundle) = args.getInt(KEY_MAX_SELECTION)
    }

    private object Request {
        const val MEDIA_ACCESS_PERMISSION = 1
        const val ADD_PHOTO_CAMERA = 2
        const val ADD_PHOTO_GALLERY = 3
    }

    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var bottomSheetDialog: FrameLayout
    private lateinit var galleryContainer: FrameLayout
    private lateinit var cameraContainer: FrameLayout
    private lateinit var emptyText: TextView
    private lateinit var photos: RecyclerView
    private lateinit var permissionTextView: TextView
    private lateinit var grantTextView: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var photoAdapter: ImagePickerAdapter

    private lateinit var vm: PickerViewModel

    private var behavior: BottomSheetBehavior<FrameLayout>? = null

    private var snackBar: Snackbar? = null

    private val cornerRadiusOutValue = TypedValue()

    private lateinit var contextWrapper: ContextThemeWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vm = ViewModelProvider(this).get(PickerViewModel::class.java)
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
        ).apply {
            contextWrapper.theme.resolveAttribute(R.attr.pickerCornerRadius, cornerRadiusOutValue, true)

            coordinatorLayout = findViewById(R.id.coordinatorLayout)
            bottomSheetDialog = findViewById(R.id.bottom_sheet)
            galleryContainer = findViewById(R.id.gallery_container)
            cameraContainer = findViewById(R.id.camera_container)
            emptyText = findViewById(R.id.empty_text)
            photos = findViewById(R.id.photos)
            permissionTextView = findViewById(R.id.permission_text_view)
            grantTextView = findViewById(R.id.grant_text_view)
            progressBar = findViewById(R.id.progress_bar)

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

            cameraContainer.isVisible = getAllowCamera(requireArguments())
            galleryContainer.setOnClickListener { pickImageGallery() }
            cameraContainer.setOnClickListener { pickImageCamera() }
            grantTextView.setOnClickListener { grantPermissions() }

            pickerBottomSheetCallback.setMargin(requireContext().resources.getDimensionPixelSize(cornerRadiusOutValue.resourceId))
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        coordinatorLayout.doOnLayout {
            behavior = BottomSheetBehavior.from(bottomSheetDialog).apply {
                addBottomSheetCallback(pickerBottomSheetCallback)
                isHideable = true
                skipCollapsed = false
                peekHeight =
                    if (orientation() == Configuration.ORIENTATION_LANDSCAPE) it.measuredHeight / 2
                    else BottomSheetBehavior.PEEK_HEIGHT_AUTO
            }
        }

        if (savedInstanceState == null) updateState()

        vm.hasPermission.observe(viewLifecycleOwner, { handlePermission(it) })
        vm.selected.observe(viewLifecycleOwner, { handleSelected(it) })
        vm.photos.observe(viewLifecycleOwner, { handlePhotos(it) })
        vm.inProgress.observe(viewLifecycleOwner, {
            photos.visibility = if (it) View.INVISIBLE else View.VISIBLE
            progressBar.visibility = if (it) View.VISIBLE else View.GONE
        })
        vm.hasContent.observe(viewLifecycleOwner, {
            pickerBottomSheetCallback.setNeedTransformation(it)
            if (it) remeasureContentDialog()
        })
        vm.maxSelectionReached.observe(viewLifecycleOwner, {
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
        coordinatorLayout.doOnLayout {
            val heightLp = bottomSheetDialog.layoutParams
            heightLp.height = coordinatorLayout.measuredHeight + requireContext().resources.getDimensionPixelSize(cornerRadiusOutValue.resourceId)
            bottomSheetDialog.layoutParams = heightLp
        }
    }

    private fun handlePermission(hasPermission: Boolean) {
        permissionTextView.visibility = if (hasPermission) View.GONE else View.VISIBLE
        grantTextView.visibility = if (hasPermission) View.GONE else View.VISIBLE

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
                snackBar = Snackbar.make(coordinatorLayout, "", Snackbar.LENGTH_INDEFINITE)
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
        emptyText.visibility =
            if (photos.isEmpty() && vm.hasPermission.value == true) View.VISIBLE
            else View.GONE
    }

    private fun loadPhotos() {
        vm.setInProgress(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val projection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED
                )
            } else {
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED
                )
            }

            val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            requireContext().contentResolver.query(
                images,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            ).use { vm.setPhotos(it) }
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
            bottomSheetDialog.translationY = -calculatedSpacing
            bottomSheetDialog.setPadding(0, calculatedSpacing.toInt(), 0, 0)
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

    interface Callback {
        fun onImagesPicked(photos: List<Uri>)
    }

}