package lv.chi.photopicker.ext

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

internal inline fun <reified T> Fragment.parentAs() = (parentFragment ?: activity) as? T

internal fun Fragment.isPermissionGranted(permission: String): Boolean {
    return context?.let {
        ContextCompat.checkSelfPermission(it.applicationContext, permission) == PackageManager.PERMISSION_GRANTED
    } ?: throw IllegalStateException("Fragment is not attached to any context")
}