package com.multiregionvpn.deviceowner

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver for Device Owner policies in debug builds.
 * This is used to bypass VPN permission dialogs in automated E2E tests.
 */
class TestDeviceOwnerReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        android.util.Log.i("TestDeviceOwner", "Device Admin Enabled")
    }
}
