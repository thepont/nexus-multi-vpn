package com.multiregionvpn.ui.settings

import android.content.Context
import com.multiregionvpn.core.provider.ProviderRegistry
import com.multiregionvpn.data.database.ProviderAccountEntity
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.data.security.CredentialEncryption
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class to migrate existing ProviderCredentials to ProviderAccountEntity
 * or create a new provider account from credentials.
 */
@Singleton
class AddProviderAccountHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val providerRegistry: ProviderRegistry,
    private val credentialEncryption: CredentialEncryption
) {
    
    /**
     * Migrates existing NordVPN credentials from ProviderCredentials to ProviderAccountEntity
     */
    suspend fun migrateNordVpnCredentialsToAccount(): Boolean {
        val creds = settingsRepository.getProviderCredentials("nordvpn") ?: return false
        
        // Check if account already exists (get first value from flow)
        val existingAccounts = settingsRepository.getProviderAccountsByProvider("nordvpn")
        val existingList = existingAccounts.first()
        if (existingList.isNotEmpty()) {
            android.util.Log.d("AddProviderAccountHelper", "NordVPN account already exists, skipping migration")
            return true
        }
        
        // Create provider account from credentials
        val credentialsMap = mapOf(
            "username" to creds.username,
            "password" to creds.password
        )
        val credentialsJson = Gson().toJson(credentialsMap)
        val encrypted = credentialEncryption.encrypt(credentialsJson.toByteArray())
        
        val account = ProviderAccountEntity(
            id = UUID.randomUUID().toString(),
            providerId = "nordvpn",
            displayLabel = "NordVPN Account",
            encryptedCredentials = encrypted,
            lastAuthState = null,
            updatedAt = System.currentTimeMillis()
        )
        
        settingsRepository.saveProviderAccount(account)
        android.util.Log.d("AddProviderAccountHelper", "Migrated NordVPN credentials to provider account")
        return true
    }
    
    /**
     * Creates a new provider account from username and password
     */
    suspend fun createNordVpnAccount(username: String, password: String, displayLabel: String = "NordVPN Account"): Boolean {
        val credentialsMap = mapOf(
            "username" to username,
            "password" to password
        )
        val credentialsJson = Gson().toJson(credentialsMap)
        val encrypted = credentialEncryption.encrypt(credentialsJson.toByteArray())
        
        val account = ProviderAccountEntity(
            id = UUID.randomUUID().toString(),
            providerId = "nordvpn",
            displayLabel = displayLabel,
            encryptedCredentials = encrypted,
            lastAuthState = null,
            updatedAt = System.currentTimeMillis()
        )
        
        settingsRepository.saveProviderAccount(account)
        android.util.Log.d("AddProviderAccountHelper", "Created NordVPN provider account: $displayLabel")
        return true
    }
}

