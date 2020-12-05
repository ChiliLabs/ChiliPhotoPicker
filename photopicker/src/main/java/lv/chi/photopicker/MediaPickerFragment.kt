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
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lv.chi.photopicker.MediaPickerViewModel.Companion.SELECTION_UNDEFINED
import lv.chi.photopicker.adapter.MediaPickerAdapter
import lv.chi.photopicker.adapter.SelectableMedia
import lv.chi.photopicker.ext.*
import lv.chi.photopicker.utils.CameraActivity
import lv.chi.photopicker.utils.NonDismissibleBehavior
import lv.chi.photopicker.utils.SpacingItemDecoration

class MediaPickerFragment : BottomSheetDialogFragment() {

    companion object {
        private val TAG = MediaPickerFragment::class.java.simpleName

        private const val KEY_MULTIPLE = "KEY_MULTIPLE"
        private const val KEY_ALLOW_CAMERA = "KEY_ALLOW_CAMERA"
        private const val KEY_THEME = "KEY_THEME"
        private const val KEY_MAX_SELECTION = "KEY_MAX_SELECTION"
        private const val KEY_PICKER_MODE = "KEY_PICKER_MODE"

        fun newInstance(
            multiple: Boolean = false,
            allowCamera: Boolean = false,
            maxSelection: Int = SELECTION_UNDEFINED,
            pickerMode: PickerMode = PickerMode.MEDIA,
            @StyleRes theme: Int = R.style.MediaPicker_Light
        ) = MediaPickerFragment().apply {
            arguments = Bundle().apply {
                putBoolean(KEY_MULTIPLE, multiple)
                putBoolean(KEY_ALLOW_CAMERA, allowCamera)
                putInt(KEY_MAX_SELECTION, maxSelection)
                putSerializable(KEY_PICKER_MODE, pickerMode)
                putInt(KEY_THEME, theme)
            }
        }

        private fun getTheme(args: Bundle) = args.getInt(KEY_THEME)
        private fun getAllowCamera(args: Bundle) = args.getBoolean(KEY_ALLOW_CAMERA)
        private fun getAllowMultiple(args: Bundle) = args.getBoolean(KEY_MULTIPLE)
        private fun getMaxSelection(args: Bundle) = args.getInt(KEY_MAX_SELECTION)
        private fun getPickerMode(args: Bundle) = args.getSerializable(KEY_PICKER_MODE) as PickerMode
    }

    enum class PickerMode {
        IMAGE,
        VIDEO,
        MEDIA  // Image & video
    }

    private object Request {
        const val MEDIA_ACCESS_PERMISSION = 1
        const val ADD_MEDIA_CAMERA = 2
        const val ADD_MEDIA_GALLERY = 3
    }

    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var bottomSheet: FrameLayout
    private lateinit var galleryButton: MaterialButton
    private lateinit var cameraButton: MaterialButton
    private lateinit var emptyTextView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var permissionTextView: TextView
    private lateinit var grantTextView: TextView
    private lateinit var progressBar: FrameLayout

    private lateinit var mediaPickerAdapter: MediaPickerAdapter

    private lateinit var viewModel: MediaPickerViewModel

    private lateinit var behavior: BottomSheetBehavior<FrameLayout>

    private var toast: Toast? = null
    private var snackBar: Snackbar? = null

    private val cornerRadiusOutValue = TypedValue()

    private lateinit var contextWrapper: ContextThemeWrapper

    private lateinit var pickerMode: PickerMode

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        error.printStackTrace()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this).get(MediaPickerViewModel::class.java)
        viewModel.setMaxSelectionCount(getMaxSelection(requireArguments()))

        contextWrapper = ContextThemeWrapper(context, getTheme(requireArguments()))

        pickerMode = getPickerMode(requireArguments())

        mediaPickerAdapter = MediaPickerAdapter(
            onMediaClick = ::onMediaClicked,
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
            R.layout.fragment_media_picker,
            container,
            false
        ).apply {
            contextWrapper.theme.resolveAttribute(R.attr.pickerCornerRadius, cornerRadiusOutValue, true)

            coordinatorLayout = findViewById(R.id.coordinatorLayout)
            bottomSheet = findViewById(R.id.bottomSheet)
            galleryButton = findViewById(R.id.galleryButton)
            cameraButton = findViewById(R.id.cameraButton)
            emptyTextView = findViewById(R.id.emptyTextView)
            recyclerView = findViewById(R.id.recyclerView)
            permissionTextView = findViewById(R.id.permissionTextView)
            grantTextView = findViewById(R.id.grantTextView)
            progressBar = findViewById(R.id.progressBar)

            recyclerView.run {
                setHasFixedSize(true)
                adapter = mediaPickerAdapter
                val margin = context.resources.getDimensionPixelSize(R.dimen.margin_2dp)
                addItemDecoration(SpacingItemDecoration(margin, margin, margin, margin))
                val layoutManager = GridLayoutManager(
                    requireContext(),
                    if (orientation() == Configuration.ORIENTATION_LANDSCAPE) 5 else 3,
                    RecyclerView.VERTICAL,
                    false
                )
                layoutManager.isItemPrefetchEnabled = true
                this@run.layoutManager = layoutManager
            }

            cameraButton.isVisible = getAllowCamera(requireArguments())
            galleryButton.setOnClickListener {
                when (pickerMode) {
                    PickerMode.IMAGE -> pickImageGallery()
                    PickerMode.VIDEO -> pickVideoGallery()
                    PickerMode.MEDIA -> pickMediaGallery()
                }
            }
            cameraButton.setOnClickListener { pickImageCamera() }
            grantTextView.setOnClickListener { grantPermissions() }

            pickerBottomSheetCallback.setMargin(requireContext().resources.getDimensionPixelSize(cornerRadiusOutValue.resourceId))
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        coordinatorLayout.doOnLayout {
            behavior = BottomSheetBehavior.from(bottomSheet).apply {
                addBottomSheetCallback(pickerBottomSheetCallback)
                isHideable = true
                skipCollapsed = false
                peekHeight =
                    if (orientation() == Configuration.ORIENTATION_LANDSCAPE) it.measuredHeight / 2
                    else BottomSheetBehavior.PEEK_HEIGHT_AUTO
            }
        }

        if (savedInstanceState == null) updateState()

        viewModel.getHasPermission().observe(viewLifecycleOwner, { handlePermission(it) })
        viewModel.getSelected().observe(viewLifecycleOwner, { handleSelected(it) })
        viewModel.getMedia().observe(viewLifecycleOwner, { handleMedia(it) })
        viewModel.getInProgress().observe(viewLifecycleOwner, {
            recyclerView.visibility = if (it) View.INVISIBLE else View.VISIBLE
            progressBar.visibility = if (it) View.VISIBLE else View.GONE
        })
        viewModel.getHasContent().observe(viewLifecycleOwner, {
            pickerBottomSheetCallback.setNeedTransformation(it)
            if (it) remeasureContentDialog()
        })
        viewModel.getMaxSelectionReached().observe(viewLifecycleOwner, {
            val max = getMaxSelection(requireArguments())
            toast?.cancel()
            toast = Toast.makeText(
                requireContext(),
                resources.getQuantityString((R.plurals.picker_max_selection_reached), max, max),
                Toast.LENGTH_SHORT
            )
            toast?.show()
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
            Request.ADD_MEDIA_CAMERA -> {
                if (resultCode == Activity.RESULT_OK) {
                    Intents.getCameraResult(data)?.let { output ->
                        val type = when (getCaptureMode()) {
                            CameraActivity.CaptureMode.IMAGE -> SelectableMedia.Type.IMAGE
                            CameraActivity.CaptureMode.VIDEO -> SelectableMedia.Type.VIDEO
                        }
                        parentAs<Callback>()?.onCameraMediaPicked(
                            SelectableMedia.fromCamera(type = type, uri = output)
                        )
                        dismiss()
                    }
                }
            }
            Request.ADD_MEDIA_GALLERY -> {
                if (resultCode == Activity.RESULT_OK) {
                    Intents.getUriResult(data)?.let { uris ->
                        parentAs<Callback>()?.onGalleryMediaPicked(uris)
                        dismiss()
                    }
                }
            }
            else ->
                super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun onMediaClicked(selectableMedia: SelectableMedia) {
        if (getAllowMultiple(requireArguments())) {
            viewModel.toggleSelected(selectableMedia)
        } else {
            parentAs<Callback>()?.onMediaPicked(listOf(selectableMedia))
            dismiss()
        }
    }

    private fun remeasureContentDialog() {
        coordinatorLayout.doOnLayout {
            val heightLp = bottomSheet.layoutParams
            heightLp.height =
                coordinatorLayout.measuredHeight + requireContext().resources.getDimensionPixelSize(cornerRadiusOutValue.resourceId)
            bottomSheet.layoutParams = heightLp
        }
    }

    private fun handlePermission(hasPermission: Boolean) {
        permissionTextView.visibility = if (hasPermission) View.GONE else View.VISIBLE
        grantTextView.visibility = if (hasPermission) View.GONE else View.VISIBLE

        recyclerView.visibility = if (hasPermission) View.VISIBLE else View.INVISIBLE
    }

    private fun handleSelected(selected: List<SelectableMedia>) {
        if (selected.isEmpty()) {
            snackBar?.dismiss()
            snackBar = null
        } else {
            val count = selected.count()
            if (snackBar == null) {
                val view = LayoutInflater.from(contextWrapper)
                        .inflate(R.layout.view_snackbar, null)
                snackBar = Snackbar.make(coordinatorLayout, "", Snackbar.LENGTH_INDEFINITE)
                    .setBehavior(NonDismissibleBehavior())
                (snackBar?.view as? ViewGroup)?.apply {
                    setPadding(0, 10, 0, 10)
                    removeAllViews()
                    addView(view)
                    findViewById<MaterialButton>(R.id.cancelButton).setOnClickListener { viewModel.clearSelected() }
                    findViewById<MaterialButton>(R.id.selectButton).setOnClickListener { uploadSelected() }
                }
                snackBar?.show()
            }
            snackBar?.view?.findViewById<TextView>(R.id.countView)?.text =
                resources.getQuantityString(R.plurals.picker_selected_count, count, count)
        }
    }

    private fun handleMedia(media: List<SelectableMedia>) {
        viewModel.setInProgress(false)
        mediaPickerAdapter.submitList(media.toMutableList())
        emptyTextView.visibility =
                if (media.isEmpty() && viewModel.getHasPermission().value == true) View.VISIBLE
                else View.GONE
    }

    private fun loadImages() {
        viewModel.setInProgress(true)
        lifecycleScope.launch(Dispatchers.IO + exceptionHandler) {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            requireContext().contentResolver.query(
                uri,
                getProjection(),
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            )?.use(viewModel::setMedia)
        }
    }

    private fun loadVideos() {
        viewModel.setInProgress(true)
        lifecycleScope.launch(Dispatchers.IO + exceptionHandler) {
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

            requireContext().contentResolver.query(
                uri,
                getProjection(),
                null,
                null,
                MediaStore.Video.Media.DATE_ADDED + " DESC"
            )?.use(viewModel::setMedia)
        }
    }

    private fun loadMedia() {
        viewModel.setInProgress(true)
        lifecycleScope.launch(Dispatchers.IO + exceptionHandler) {
            val uri = MediaStore.Files.getContentUri("external")

            val selection = (MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    + " OR "
                    + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)

            requireContext().contentResolver.query(
                uri,
                getProjection(),
                selection,
                null,
                MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
            )?.use(viewModel::setMedia)
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
            viewModel.setHasPermission(true)
            when (pickerMode) {
                PickerMode.IMAGE -> loadImages()
                PickerMode.VIDEO -> loadVideos()
                PickerMode.MEDIA -> loadMedia()
            }
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

        override fun onSlide(v: View, slideOffset: Float) {
            if (!needTransformation) return
            val calculatedSpacing = calculateSpacing(slideOffset)
            bottomSheet.translationY = -calculatedSpacing
            bottomSheet.setPadding(0, calculatedSpacing.toInt(), 0, 0)
        }

        fun setMargin(margin: Int) {
            this.margin = margin
        }

        fun setNeedTransformation(need: Boolean) {
            needTransformation = need
        }

        private fun calculateSpacing(progress: Float): Float = margin * progress
    }

    private fun pickImageCamera() {
        startActivityForResult(
            CameraActivity.newIntent(requireContext(), getCaptureMode()),
            Request.ADD_MEDIA_CAMERA
        )
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
            Request.ADD_MEDIA_GALLERY
        )
    }

    private fun pickVideoGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, getAllowMultiple(requireArguments()))
        }

        startActivityForResult(
            Intent.createChooser(intent, getString(R.string.picker_select_video)),
            Request.ADD_MEDIA_GALLERY
        )
    }

    private fun pickMediaGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            type = "image/* video/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, getAllowMultiple(requireArguments()))
        }

        startActivityForResult(
            Intent.createChooser(intent, getString(R.string.picker_select_media)),
            Request.ADD_MEDIA_GALLERY
        )
    }

    private fun uploadSelected() {
        val selected = ArrayList(viewModel.getSelected().value ?: emptyList())

        parentAs<Callback>()?.onMediaPicked(selected)
        dismiss()
    }

    private fun getCaptureMode(): CameraActivity.CaptureMode {
        return when (pickerMode) {
            PickerMode.IMAGE -> CameraActivity.CaptureMode.IMAGE
            PickerMode.VIDEO -> CameraActivity.CaptureMode.VIDEO
            PickerMode.MEDIA -> CameraActivity.CaptureMode.IMAGE
        }
    }

    private fun orientation() = requireContext().resources.configuration.orientation

    private fun getProjection(): Array<String> {
        val projection = when (pickerMode) {
            PickerMode.IMAGE -> mutableListOf(
                MediaStore.Images.ImageColumns._ID,
                MediaStore.Images.ImageColumns.DATA,
                MediaStore.Images.ImageColumns.DATE_ADDED,
                MediaStore.Images.ImageColumns.DATE_MODIFIED,
                MediaStore.Images.ImageColumns.DISPLAY_NAME,
                MediaStore.Images.ImageColumns.MIME_TYPE,
                MediaStore.Images.ImageColumns.SIZE,
                MediaStore.Images.ImageColumns.WIDTH,
                MediaStore.Images.ImageColumns.HEIGHT
            )
            PickerMode.VIDEO -> mutableListOf(
                MediaStore.Video.VideoColumns._ID,
                MediaStore.Video.VideoColumns.DATA,
                MediaStore.Video.VideoColumns.DATE_ADDED,
                MediaStore.Video.VideoColumns.DATE_MODIFIED,
                MediaStore.Video.VideoColumns.DISPLAY_NAME,
                MediaStore.Video.VideoColumns.MIME_TYPE,
                MediaStore.Video.VideoColumns.SIZE,
                MediaStore.Video.VideoColumns.WIDTH,
                MediaStore.Video.VideoColumns.HEIGHT
            )
            PickerMode.MEDIA -> mutableListOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.WIDTH,
                MediaStore.Files.FileColumns.HEIGHT
            )
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            when (pickerMode) {
                PickerMode.IMAGE -> {
                    projection.add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                    projection.add(MediaStore.Images.ImageColumns.DATE_TAKEN)
                }
                PickerMode.VIDEO -> {
                    projection.add(MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME)
                    projection.add(MediaStore.Video.VideoColumns.DATE_TAKEN)
                    projection.add(MediaStore.Video.VideoColumns.DURATION)
                }
                PickerMode.MEDIA -> {
                    projection.add(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                    projection.add(MediaStore.Files.FileColumns.DATE_TAKEN)
                    projection.add(MediaStore.Files.FileColumns.DURATION)
                }
            }
        }
        return projection.toTypedArray()
    }

    interface Callback {
        fun onMediaPicked(media: List<SelectableMedia>)
        fun onCameraMediaPicked(media: SelectableMedia)
        fun onGalleryMediaPicked(media: List<Uri>)
    }

}