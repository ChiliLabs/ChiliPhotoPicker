package lv.chi.photopicker

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialog

class PickerDialog(
    context: Context,
    theme: Int = R.style.ChiliPhotoPicker_Light
) : AppCompatDialog(context, theme) {

    init {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            attributes.dimAmount = 0.6f
            attributes.flags += WindowManager.LayoutParams.FLAG_DIM_BEHIND
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }
}