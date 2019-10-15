package lv.chi.photopicker

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialog

class PickerDialog(context: Context, theme: Int = 0) : AppCompatDialog(
    context,
    getThemeResId(context, theme)
) {

    init {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    companion object {
        @SuppressLint("PrivateResource")
        fun getThemeResId(context: Context, themeId: Int): Int {
            var theme = themeId
            if (theme == 0) {
                val outValue = TypedValue()
                theme = if (context.theme.resolveAttribute(R.attr.bottomSheetDialogTheme, outValue, true))
                    outValue.resourceId
                else R.style.Theme_Design_Light_BottomSheetDialog
            }
            return theme
        }
    }

}