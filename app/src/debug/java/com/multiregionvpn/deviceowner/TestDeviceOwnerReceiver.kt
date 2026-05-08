package com.multiregionvpn.deviceowner

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * A mock DeviceAdminReceiver for testing purposes in debug builds.
 */
class TestDeviceOwnerReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }
}
