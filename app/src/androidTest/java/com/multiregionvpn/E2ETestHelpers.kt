package com.multiregionvpn

import android.content.Context
import androidx.test.uiautomator.UiDevice
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared E2E Test Helpers
 * 
 * Utilities for both Mobile and TV E2E tests.
 */
object E2ETestHelpers {

    /**
     * Grant VPN permission via ADB shell command
     */
    fun grantVpnPermission(uiDevice: UiDevice, context: Context) {
        try {
            val packageName = context.packageName
            uiDevice.executeShellCommand("appops set $packageName ACTIVATE_VPN allow")
            Thread.sleep(500)
        } catch (e: Exception) {
            android.util.Log.w("E2ETestHelpers", "Failed to grant VPN permission: ${e.message}")
        }
    }

    /**
     * Set up test VPN configs (UK and FR)
     */
    fun setupTestVpnConfigs(repository: SettingsRepository) = runBlocking {
        val ukConfig = VpnConfig(
            id = "e2e_test_uk",
            name = "E2E Test UK",
            regionId = "uk",
            templateId = "nordvpn",
            serverHostname = "uk1860.nordvpn.com"
        )
        
        val frConfig = VpnConfig(
            id = "e2e_test_fr",
            name = "E2E Test FR",
            regionId = "fr",
            templateId = "nordvpn",
            serverHostname = "fr842.nordvpn.com"
        )
        
        repository.saveVpnConfig(ukConfig)
        repository.saveVpnConfig(frConfig)
        
        Pair(ukConfig, frConfig)
    }

    /**
     * Set up test provider credentials (NordVPN)
     */
    fun setupTestCredentials(repository: SettingsRepository) = runBlocking {
        val credentials = ProviderCredentials(
            templateId = "nordvpn",
            username = "test_user",
            password = "test_password"
        )
        repository.saveProviderCredentials(credentials)
        credentials
    }

    /**
     * Set up test app rule
     */
    fun setupTestAppRule(
        repository: SettingsRepository,
        packageName: String,
        vpnConfigId: String
    ) = runBlocking {
        val appRule = AppRule(
            packageName = packageName,
            vpnConfigId = vpnConfigId
        )
        repository.saveAppRule(appRule)
        appRule
    }

    /**
     * Clean all test data from database
     */
    fun cleanDatabase(repository: SettingsRepository) = runBlocking {
        repository.clearAllVpnConfigs()
        repository.clearAllAppRules()
        // Note: Provider credentials don't have a clear method yet
    }

    /**
     * Check if internet connectivity works
     * 
     * @return true if can reach google.com, false otherwise
     */
    fun checkInternetConnectivity(): Boolean {
        return try {
            val url = URL("https://www.google.com/generate_204")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            responseCode in 200..299
        } catch (e: IOException) {
            android.util.Log.w("E2ETestHelpers", "Internet check failed: ${e.message}")
            false
        }
    }

    /**
     * Wait for a condition to be true, with timeout
     * 
     * @param timeoutMs Maximum time to wait in milliseconds
     * @param pollIntervalMs How often to check the condition
     * @param condition Lambda that returns true when condition is met
     * @return true if condition met within timeout, false otherwise
     */
    fun waitForCondition(
        timeoutMs: Long = 10000,
        pollIntervalMs: Long = 500,
        condition: () -> Boolean
    ): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) {
                return true
            }
            Thread.sleep(pollIntervalMs)
        }
        
        return false
    }

    /**
     * Get human-readable region name from region ID
     */
    fun getRegionDisplayName(regionId: String): String {
        return when (regionId.lowercase()) {
            "uk" -> "United Kingdom"
            "us" -> "United States"
            "fr" -> "France"
            "de" -> "Germany"
            "ca" -> "Canada"
            "au" -> "Australia"
            "jp" -> "Japan"
            "nl" -> "Netherlands"
            "se" -> "Sweden"
            "ch" -> "Switzerland"
            else -> regionId.uppercase()
        }
    }

    /**
     * Test data class for server groups
     */
    data class TestServerGroup(
        val id: String,
        val name: String,
        val regionId: String,
        val serverCount: Int
    )

    /**
     * Test data class for app rules
     */
    data class TestAppRule(
        val packageName: String,
        val appName: String,
        val vpnConfigId: String?,
        val routedGroupId: String?
    )
}

/**
 * Extension function: Clear all VPN configs from repository
 */
suspend fun SettingsRepository.clearAllVpnConfigs() {
    val configs = getAllVpnConfigs().kotlinx.coroutines.flow.first()
    configs.forEach { config ->
        deleteVpnConfig(config.id)
    }
}

/**
 * Extension function: Clear all app rules from repository
 */
suspend fun SettingsRepository.clearAllAppRules() {
    val rules = getAllAppRules().kotlinx.coroutines.flow.first()
    rules.forEach { rule ->
        appRuleDao.delete(rule)
    }
}

/**
 * Extension function: Get app rule by package name
 */
suspend fun SettingsRepository.getAppRuleByPackageName(packageName: String): AppRule? {
    return appRuleDao.getRuleByPackageName(packageName)
}

