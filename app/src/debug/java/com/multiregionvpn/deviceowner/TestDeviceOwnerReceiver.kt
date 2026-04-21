package com.multiregionvpn.deviceowner

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver for Device Owner / Device Admin functionality in debug builds.
 * Used for system-level testing (e.g., CA certificate installation).
 */
class TestDeviceOwnerReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i("TestDeviceOwner", "Device Admin enabled")
    }
}
