package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// This class holds the final, ready-to-use config
data class PreparedVpnConfig(
    val vpnConfig: VpnConfig,
    val ovpnFileContent: String,
    val authFile: File? // A temporary file holding username/password
)

@Singleton
class VpnTemplateService @Inject constructor(
    private val nordVpnApi: NordVpnApiService,
    private val settingsRepo: SettingsRepository,
    // Inject the app's cache directory
    @ApplicationContext private val context: Context 
) {

    /**
     * The main public function.
     * Takes a VpnConfig from our DB and returns a complete,
     * ready-to-use config object for the VpnConnectionManager.
     */
    suspend fun prepareConfig(config: VpnConfig): PreparedVpnConfig {
        return when (config.templateId) {
            "nordvpn" -> prepareNordVpnConfig(config)
            // "custom_ovpn" -> prepareCustomConfig(config)
            else -> throw IllegalArgumentException("Unknown templateId: ${config.templateId}")
        }
    }

    private suspend fun prepareNordVpnConfig(config: VpnConfig): PreparedVpnConfig {
        // 1. Get the base .ovpn config file from NordVPN
        // Try direct download URL first (public access), fall back to API if needed
        val baseConfig = withContext(Dispatchers.IO) {
            try {
                // Try direct download URL (publicly accessible)
                val directUrl = "https://downloads.nordcdn.com/configs/files/ovpn_udp/servers/${config.serverHostname}.udp.ovpn"
                val response = java.net.URL(directUrl).openConnection()
                response.connectTimeout = 10000
                response.readTimeout = 10000
                response.getInputStream().bufferedReader().readText()
            } catch (e: Exception) {
                // Fall back to API if direct download fails
                try {
                    val responseBody = nordVpnApi.getOvpnConfig(config.serverHostname)
                    responseBody.string()
                } catch (apiError: Exception) {
                    throw Exception("Failed to fetch OpenVPN config from both direct URL and API: ${e.message}, API error: ${apiError.message}", e)
                }
            }
        }

        // 2. Get the credentials for "nordvpn" from our secure storage
        val creds = settingsRepo.getProviderCredentials("nordvpn")
            ?: throw Exception("NordVPN credentials are not set.")

        // 3. Create the auth file
        // OpenVPN clients can read credentials from a file.
        // This is more secure than passing them as arguments.
        val authFile = File(context.cacheDir, "nord_auth_${config.id}.txt")
        withContext(Dispatchers.IO) {
            authFile.writeText("${creds.username}\n${creds.password}")
        }
        
        // 4. Modify the .ovpn config string
        val modifiedConfig = baseConfig
            // Find the default "auth-user-pass" line
            .replace(
                "auth-user-pass",
                // Replace it with a line pointing to our new auth file
                "auth-user-pass ${authFile.absolutePath}"
            )

        return PreparedVpnConfig(
            vpnConfig = config,
            ovpnFileContent = modifiedConfig,
            authFile = authFile
        )
    }
}

