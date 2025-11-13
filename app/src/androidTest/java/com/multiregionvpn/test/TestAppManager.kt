package com.multiregionvpn.test

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Utility class for managing test apps during testing.
 * 
 * Handles:
 * - Installing test apps
 * - Launching test apps
 * - Interacting with test apps (clicking buttons, reading responses)
 * - Uninstalling test apps
 */
object TestAppManager {
    private const val TAG = "TestAppManager"
    
    /**
     * Test app package names
     */
    enum class TestApp(val packageName: String, val displayName: String) {
        UK("com.example.testapp.uk", "UK Test App"),
        FR("com.example.testapp.fr", "FR Test App"),
        DNS("com.example.testapp.dns", "DNS Test App")
    }
    
    /**
     * Checks if a test app is installed
     */
    fun isAppInstalled(context: Context, app: TestApp): Boolean {
        return try {
            context.packageManager.getPackageInfo(app.packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * Launches a test app and waits for it to be ready
     */
    fun launchApp(context: Context, device: UiDevice, app: TestApp): Boolean {
        Log.d(TAG, "Launching app: ${app.displayName}")
        
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Log.e(TAG, "Cannot find launch intent for ${app.packageName}")
            return false
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        
        // Wait for app to launch
        val launched = device.wait(
            Until.hasObject(By.pkg(app.packageName).depth(0)),
            5000
        )
        
        if (!launched) {
            Log.e(TAG, "App ${app.displayName} did not launch within timeout")
            return false
        }
        
        Log.d(TAG, "App ${app.displayName} launched successfully")
        return true
    }
    
    /**
     * Clicks the "Fetch" button in a test app
     */
    fun clickFetchButton(device: UiDevice, app: TestApp): Boolean {
        Log.d(TAG, "Clicking Fetch button in ${app.displayName}")
        
        val fetchButton = device.findObject(By.res("${app.packageName}:id/btn_fetch"))
        if (fetchButton == null) {
            Log.e(TAG, "Cannot find Fetch button in ${app.displayName}")
            return false
        }
        
        fetchButton.click()
        Log.d(TAG, "Fetch button clicked")
        return true
    }
    
    /**
     * Gets the response text from a test app
     */
    fun getResponseText(device: UiDevice, app: TestApp, timeoutMs: Long = 10000): String? {
        Log.d(TAG, "Getting response text from ${app.displayName}")
        
        val responseView = device.wait(
            Until.findObject(By.res("${app.packageName}:id/tv_response")),
            timeoutMs
        )
        
        if (responseView == null) {
            Log.e(TAG, "Cannot find response text view in ${app.displayName}")
            return null
        }
        
        val responseText = responseView.text
        Log.d(TAG, "Response text: $responseText")
        return responseText
    }
    
    /**
     * Waits for a specific response text in a test app
     */
    fun waitForResponseText(
        device: UiDevice,
        app: TestApp,
        expectedText: String,
        timeoutMs: Long = 10000
    ): Boolean {
        Log.d(TAG, "Waiting for response '$expectedText' in ${app.displayName}")
        
        val responseView = device.wait(
            Until.findObject(By.res("${app.packageName}:id/tv_response")),
            timeoutMs
        )
        
        if (responseView == null) {
            Log.e(TAG, "Cannot find response text view")
            return false
        }
        
        val actualText = responseView.text
        val matches = actualText == expectedText
        
        if (matches) {
            Log.d(TAG, "✅ Response matches expected: $expectedText")
        } else {
            Log.w(TAG, "❌ Response mismatch. Expected: $expectedText, Got: $actualText")
        }
        
        return matches
    }
    
    /**
     * Uninstalls a test app
     */
    fun uninstallApp(context: Context, app: TestApp): Boolean {
        Log.d(TAG, "Uninstalling app: ${app.displayName}")
        
        return try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = android.net.Uri.parse("package:${app.packageName}")
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error uninstalling app", e)
            false
        }
    }
    
    /**
     * Installs a test app from APK file
     * Note: This requires the APK to be accessible on the device
     */
    fun installApp(device: UiDevice, apkPath: String): Boolean {
        Log.d(TAG, "Installing app from: $apkPath")
        
        // Installation would typically be done via adb before tests run
        // This is a placeholder for programmatic installation if needed
        Log.w(TAG, "Programmatic installation not implemented. Use: adb install $apkPath")
        return false
    }
}


