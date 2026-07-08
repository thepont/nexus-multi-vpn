package com.multiregionvpn.deviceowner

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Placeholder DeviceAdminReceiver for E2E tests.
 * This is used by automated tests to perform privileged actions in the emulator.
 */
class TestDeviceOwnerReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }
}
