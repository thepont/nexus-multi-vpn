package com.multiregionvpn.deviceowner

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.text.Charsets
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory

/**
 * Debug-only DeviceAdminReceiver that lets us promote the app to device owner during tests
 * so we can push test CA certificates via the DevicePolicyManager APIs instead of manual UI flows.
 */
class TestDeviceOwnerReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Test device owner enabled")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        Log.w(TAG, "Test device owner disable requested")
        return super.onDisableRequested(context, intent)
    }

    companion object {
        private const val TAG = "TestDeviceOwner"

        fun installCaCertificate(context: Context, certificatePem: String): Boolean {
            val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
                ?: run {
                    Log.e(TAG, "DevicePolicyManager unavailable; cannot install CA")
                    return false
                }

            val admin = ComponentName(context, TestDeviceOwnerReceiver::class.java)
            if (!devicePolicyManager.isAdminActive(admin)) {
                Log.e(TAG, "TestDeviceOwnerReceiver is not active; run dpm set-device-owner first")
                return false
            }

            val certificateBytes = parseCertificateBytes(certificatePem) ?: return false

            val alreadyInstalled = runCatching {
                devicePolicyManager.hasCaCertInstalled(admin, certificateBytes)
            }.getOrDefault(false)
            if (alreadyInstalled) {
                Log.i(TAG, "Test CA already installed")
                return true
            }

            return try {
                devicePolicyManager.installCaCert(admin, certificateBytes)
                Log.i(TAG, "Installed test CA certificate via DevicePolicyManager")
                true
            } catch (securityException: SecurityException) {
                Log.e(TAG, "Failed to install CA: ${securityException.message}", securityException)
                false
            }
        }

        fun removeCaCertificate(context: Context, certificatePem: String): Boolean {
            val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
                ?: return false
            val admin = ComponentName(context, TestDeviceOwnerReceiver::class.java)
            if (!devicePolicyManager.isAdminActive(admin)) {
                return false
            }
            val certificateBytes = parseCertificateBytes(certificatePem) ?: return false
            return try {
                devicePolicyManager.uninstallCaCert(admin, certificateBytes)
                Log.i(TAG, "Removed test CA certificate")
                true
            } catch (securityException: SecurityException) {
                Log.e(TAG, "Failed to remove CA: ${securityException.message}", securityException)
                false
            }
        }

        private fun parseCertificateBytes(pem: String): ByteArray? {
            return try {
                val certificateFactory = CertificateFactory.getInstance("X.509")
                val certificate = certificateFactory.generateCertificate(
                    ByteArrayInputStream(pem.toByteArray(Charsets.US_ASCII))
                )
                certificate.encoded
            } catch (throwable: Throwable) {
                Log.e(TAG, "Unable to parse PEM certificate", throwable)
                null
            }
        }
    }
}
