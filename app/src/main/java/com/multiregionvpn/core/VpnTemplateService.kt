package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
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
    val username: String? = null,
    val password: String? = null
)

@Singleton
class VpnTemplateService @Inject constructor(
    private val nordVpnApi: NordVpnApiService,
    private val settingsRepo: SettingsRepository,
    // Inject the app's cache directory
    @ApplicationContext private val context: Context 
) {
    companion object {
        private const val TAG = "VpnTemplateService"
    }

    /**
     * The main public function.
     * Takes a VpnConfig from our DB and returns a complete,
     * ready-to-use config object for the VpnConnectionManager.
     */
    suspend fun prepareConfig(config: VpnConfig): PreparedVpnConfig {
        return when (config.templateId) {
            "nordvpn" -> prepareNordVpnConfig(config)
            "local-test" -> prepareLocalTestConfig(config)
            // "custom_ovpn" -> prepareCustomConfig(config)
            else -> throw IllegalArgumentException("Unknown templateId: ${config.templateId}")
        }
    }

    private suspend fun prepareNordVpnConfig(config: VpnConfig): PreparedVpnConfig {
        // 1. Get the base .ovpn config file from NordVPN
        // Use Retrofit API (configured with proper DNS) instead of java.net.URL
        // to avoid DNS resolution issues when VPN interface is established
        val baseConfig = withContext(Dispatchers.IO) {
            try {
                val responseBody = nordVpnApi.getOvpnConfig(config.serverHostname)
                responseBody.string()
            } catch (e: Exception) {
                throw Exception("Failed to fetch OpenVPN config: ${e.message}", e)
            }
        }

        // 2. Get the credentials for "nordvpn" from our secure storage
        val creds = settingsRepo.getProviderCredentials("nordvpn")
            ?: throw Exception("NordVPN credentials are not set.")

        // 3. SECURE FIX: Pass credentials directly to NativeOpenVpnClient
        // instead of writing them to a temporary plaintext file in the cache directory.
        // This prevents other apps (or anyone with storage access) from reading
        // VPN credentials from /data/user/0/com.multiregionvpn/cache/nord_auth_*.txt
        
        return PreparedVpnConfig(
            vpnConfig = config,
            ovpnFileContent = baseConfig, // Don't modify auth-user-pass path
            username = creds.username,
            password = creds.password
        )
    }
    
    /**
     * Prepare config for local test OpenVPN servers (used in E2E tests).
     * These servers use username/password authentication and don't require NordVPN API.
     */
    private suspend fun prepareLocalTestConfig(config: VpnConfig): PreparedVpnConfig {
        // For local test servers, we generate a minimal OpenVPN config
        // The server hostname is already in config.serverHostname (e.g., "10.0.2.2:1199")
        val hostParts = config.serverHostname.split(":")
        val serverHost = hostParts[0]
        val serverPort = hostParts.getOrElse(1) { "1194" }
        
        // Get credentials for "local-test" template
        val creds = settingsRepo.getProviderCredentials("local-test")
            ?: throw Exception("Local test credentials are not set.")
        
        // SECURE FIX: No temporary auth files for local tests either.
        
        // For local test servers using kylemanna/openvpn image:
        // The server uses ovpn_genconfig which generates a self-signed CA
        // Use verify-x509-name for all local tests - this works reliably with OpenVPN 3
        // The server certificate CN is "server", so we verify against that
        // This matches the working routing tests (LocalRoutingTest)
        // NOTE: Embedded CA certs and CA file paths both failed with "mbed TLS: ca certificate is undefined"
        // verify-x509-name is the only method that works consistently with OpenVPN 3
        
        // Local test servers don't use compression (matching server configs)
        val compressionLine = ""
        
        val ovpnConfig = """
            client
            dev tun
            proto udp
            remote $serverHost $serverPort
            resolv-retry infinite
            nobind
            persist-key
            persist-tun
            auth-user-pass
            verb 3
            $compressionLine
            verify-x509-name server name
            remote-cert-tls server
        """.trimIndent()
        
        Log.d(TAG, "Generated OpenVPN config for ${config.name}: ${ovpnConfig.length} bytes")
        Log.d(TAG, "Using verify-x509-name server name (matching working routing tests)")
        
        return PreparedVpnConfig(
            vpnConfig = config,
            ovpnFileContent = ovpnConfig,
            username = creds.username,
            password = creds.password
        )
    }
}

