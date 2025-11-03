package com.multiregionvpn.service

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.GeoIpService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Smart setup service that auto-creates rules for known apps
 * based on user's VPN configurations and installed apps.
 * 
 * Behavior:
 * - Detects user's current region via GeoIP
 * - Compares installed apps against preset rules
 * - If preset rule requires different region AND user has VPN config for that region:
 *   → Auto-creates routing rule
 * - If preset rule matches user's region:
 *   → Ensures no rule exists (defaults to Direct Internet)
 */
class AutoRuleService(private val context: Context) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = AppDatabase.getDatabase(context)
    private val vpnConfigDao = database.vpnConfigDao()
    private val appRuleDao = database.appRuleDao()
    private val credsDao = database.providerCredentialsDao()
    private val presetRuleDao = database.presetRuleDao()
    private val settingsRepository = SettingsRepository(vpnConfigDao, appRuleDao, credsDao, presetRuleDao)
    private val geoIpService = GeoIpService()
    private val packageManager = context.packageManager
    
    fun runAutoSetup() {
        serviceScope.launch {
            try {
                val userRegion = geoIpService.getCurrentRegion()
                if (userRegion == null) {
                    Log.w(TAG, "Could not determine user region, skipping auto-setup")
                    return@launch
                }
                
                val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                val presetRules = settingsRepository.getAllPresetRules()
                
                var rulesCreated = 0
                var rulesRemoved = 0
                
                for (app in installedApps) {
                    val presetRule = presetRules.find { it.packageName == app.packageName }
                    
                    if (presetRule != null) {
                        val existingRule = settingsRepository.getAppRuleByPackageName(app.packageName)
                        
                        if (presetRule.targetRegionId != userRegion) {
                            // App should use VPN for a different region
                            val userVpn = settingsRepository.findVpnForRegion(presetRule.targetRegionId)
                            if (userVpn != null) {
                                // Check if rule already exists and points to correct VPN
                                if (existingRule == null || existingRule.vpnConfigId != userVpn.id) {
                                    settingsRepository.createAppRule(app.packageName, userVpn.id)
                                    rulesCreated++
                                    Log.d(TAG, "Auto-created rule for ${app.packageName} -> ${userVpn.name}")
                                }
                            }
                        } else {
                            // Preset rule matches user's region - ensure no rule (Direct Internet)
                            if (existingRule != null) {
                                settingsRepository.deleteAppRule(app.packageName)
                                rulesRemoved++
                                Log.d(TAG, "Removed rule for ${app.packageName} (matches user region $userRegion)")
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Auto-setup completed. Created $rulesCreated rules, removed $rulesRemoved rules")
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-setup", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "AutoRuleService"
    }
}
