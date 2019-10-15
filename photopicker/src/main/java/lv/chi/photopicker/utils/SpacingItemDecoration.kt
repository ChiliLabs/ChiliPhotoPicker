package lv.chi.photopicker.utils

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

internal class SpacingItemDecoration(
    val spacingLeft: Int = 0,
    val spacingTop: Int = 0,
    val spacingRight: Int = 0,
    val spacingBottom: Int = 0
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(out: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        out.set(spacingLeft, spacingTop, spacingRight, spacingBottom)
    }
}