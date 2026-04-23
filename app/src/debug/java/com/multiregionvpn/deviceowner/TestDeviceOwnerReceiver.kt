package com.multiregionvpn.deviceowner

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver for Device Owner / Device Admin for testing purposes.
 * This class is only present in the debug build.
 */
class TestDeviceOwnerReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }
}
