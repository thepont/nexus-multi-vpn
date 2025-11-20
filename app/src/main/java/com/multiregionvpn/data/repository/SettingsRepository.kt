package com.multiregionvpn.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.AppRuleDao
import com.multiregionvpn.data.database.PresetRule
import com.multiregionvpn.data.database.PresetRuleDao
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.ProviderCredentialsDao
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.database.VpnConfigDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vpnConfigDao: VpnConfigDao,
    val appRuleDao: AppRuleDao,  // Public for direct DB queries (bypass Flow caching)
    private val providerCredentialsDao: ProviderCredentialsDao,
    private val presetRuleDao: PresetRuleDao
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vpn_settings", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREF_DEFAULT_DNS_TUNNEL_ID = "default_dns_tunnel_id"
    }
    // VpnConfig operations
    fun getAllVpnConfigs(): Flow<List<VpnConfig>> = vpnConfigDao.getAll()
    
    suspend fun saveVpnConfig(config: VpnConfig) {
        vpnConfigDao.save(config)
    }
    
    suspend fun deleteVpnConfig(id: String) {
        vpnConfigDao.delete(id)
    }
    
    suspend fun findVpnForRegion(regionId: String): VpnConfig? {
        return vpnConfigDao.findByRegion(regionId)
    }
    
    suspend fun getVpnConfigById(id: String): VpnConfig? {
        return vpnConfigDao.getById(id)
    }
    
    // AppRule operations
    fun getAllAppRules(): Flow<List<AppRule>> = appRuleDao.getAllRules()
    
    suspend fun saveAppRule(rule: AppRule) {
        appRuleDao.save(rule)
    }
    
    suspend fun deleteAppRule(packageName: String) {
        appRuleDao.delete(packageName)
    }
    
    suspend fun getAppRuleByPackageName(packageName: String): AppRule? {
        return appRuleDao.getRuleForPackage(packageName)
    }
    
    // ProviderCredentials operations
    suspend fun getProviderCredentials(templateId: String): ProviderCredentials? {
        return providerCredentialsDao.get(templateId)
    }
    
    suspend fun saveProviderCredentials(credentials: ProviderCredentials) {
        providerCredentialsDao.save(credentials)
    }
    
    // PresetRule operations
    suspend fun getAllPresetRules(): List<PresetRule> {
        return presetRuleDao.getAll().first()
    }
    
    suspend fun createAppRule(packageName: String, vpnConfigId: String) {
        android.util.Log.i("SettingsRepository", "💾 SAVING app rule: $packageName → $vpnConfigId")
        appRuleDao.save(AppRule(packageName = packageName, vpnConfigId = vpnConfigId))
        android.util.Log.i("SettingsRepository", "✅ App rule SAVED to database")
        
        // Verify it was actually saved
        val saved = appRuleDao.getRuleForPackage(packageName)
        android.util.Log.i("SettingsRepository", "🔍 Verification query: ${saved?.packageName} → ${saved?.vpnConfigId}")
    }
    
    suspend fun updateAppRule(packageName: String, vpnConfigId: String) {
        // Update or create the app rule with new VPN config
        appRuleDao.save(AppRule(packageName = packageName, vpnConfigId = vpnConfigId))
    }
    
    // Default DNS tunnel operations
    fun setDefaultDnsTunnelId(tunnelId: String?) {
        prefs.edit().apply {
            if (tunnelId != null) {
                putString(PREF_DEFAULT_DNS_TUNNEL_ID, tunnelId)
            } else {
                remove(PREF_DEFAULT_DNS_TUNNEL_ID)
            }
            apply()
        }
    }
    
    fun getDefaultDnsTunnelId(): String? {
        return prefs.getString(PREF_DEFAULT_DNS_TUNNEL_ID, null)
    }
    
    // Test helper methods
    suspend fun clearAllAppRules() {
        appRuleDao.clearAll()
    }
    
    suspend fun clearAllVpnConfigs() {
        vpnConfigDao.clearAll()
    }
}