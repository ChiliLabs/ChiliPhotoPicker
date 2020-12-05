package lv.chi.photopicker

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialog

class PickerDialog(
    context: Context,
    theme: Int = 0
) : AppCompatDialog(context, getThemeResId(context, theme)) {

    companion object {
        fun getThemeResId(context: Context, themeId: Int): Int {
            return if (themeId == 0) {
                val outValue = TypedValue()
                if (context.theme.resolveAttribute(R.attr.bottomSheetDialogTheme, outValue, true)) {
                    outValue.resourceId
                } else {
                    R.style.Theme_MaterialComponents_Light_BottomSheetDialog
                }
            } else {
                themeId
            }
        }
    }

    init {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window?.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

}