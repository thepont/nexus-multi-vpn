package com.multiregionvpn.test

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.test.uiautomator.UiDevice
import com.multiregionvpn.deviceowner.TestDeviceOwnerReceiver

object DeviceOwnerProvisioner {
    private const val TAG = "DeviceOwnerProvisioner"

    fun ensureDeviceOwnerProvisioned(context: Context, device: UiDevice) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: run {
            Log.e(TAG, "DevicePolicyManager unavailable; cannot set device owner")
            throw IllegalStateException("DevicePolicyManager unavailable")
        }

        if (dpm.isDeviceOwnerApp(context.packageName)) {
            Log.i(TAG, "Test app already device owner")
            return
        }

        val command =
            "dpm set-device-owner ${context.packageName}/" +
                "com.multiregionvpn.deviceowner.TestDeviceOwnerReceiver"

        Log.i(TAG, "Requesting device owner via shell: $command")
        val response = device.executeShellCommand(command)
        Log.i(TAG, "dpm response: $response")

        val admin = ComponentName(context, TestDeviceOwnerReceiver::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName) || !dpm.isAdminActive(admin)) {
            throw IllegalStateException(
                "Device owner provisioning failed. Wipe emulator data and retry."
            )
        }

        Log.i(TAG, "Device owner provisioning complete")
    }

    fun installTestCaCertificate(context: Context) {
        val resourceStream = javaClass.classLoader
            ?.getResourceAsStream("certs/local_test_ca.pem")
            ?: run {
                Log.w(TAG, "Test CA resource missing; skip installation")
                return
            }

        val certificatePem = resourceStream.bufferedReader().use { it.readText() }

        val installed = TestDeviceOwnerReceiver.installCaCertificate(context, certificatePem)
        if (installed) {
            Log.i(TAG, "Test CA installed via DevicePolicyManager")
        } else {
            Log.w(TAG, "Test CA installation skipped or failed; check logs")
        }
    }
}

