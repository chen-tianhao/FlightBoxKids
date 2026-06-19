package com.flightbox.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.net.toUri

/**
 * Persists the URI of the user-chosen video folder.
 *
 * The actual read permission is held by the OS via
 * [android.content.ContentResolver.takePersistableUriPermission] and is
 * valid until the app is uninstalled or the user revokes it from system
 * settings (Settings -> Apps -> FlightBox -> Permissions).
 */
class FolderPreference(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The saved tree URI, or null if no folder has been picked yet. */
    var folderUri: Uri?
        get() = prefs.getString(KEY_URI, null)?.toUri()
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_URI) else putString(KEY_URI, value.toString())
            }.apply()
        }

    /**
     * True if the saved URI is still in the OS persisted-permissions list
     * with read access. The user can revoke this from system settings, or
     * some OEM "cleaner" apps can drop it; we use this check to surface a
     * warning so the user knows to re-pick.
     */
    fun isValid(context: Context): Boolean {
        val uri = folderUri ?: return false
        val perms = context.contentResolver.persistedUriPermissions
        return perms.any { it.uri == uri && it.isReadPermission }
    }

    fun clear() {
        folderUri = null
    }

    private companion object {
        const val PREFS_NAME = "flightbox_prefs"
        const val KEY_URI = "video_folder_uri"
    }
}