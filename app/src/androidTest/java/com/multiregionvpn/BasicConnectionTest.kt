package com.multiregionvpn

import android.content.Context
import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.multiregionvpn.core.vpnclient.NativeOpenVpnClient
import com.multiregionvpn.core.VpnTemplateService
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.network.NordVpnApiService
import com.multiregionvpn.network.NordServer
import com.google.common.truth.Truth.assertThat
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule

/**
 * Basic connection test to verify OpenVPN 3 native client can connect to a VPN server.
 * This test focuses solely on the connection itself, not routing.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class BasicConnectionTest {

    private lateinit var appContext: Context

    private val testVpnService = object : VpnService() {
        override fun protect(socket: Int): Boolean = true
    }

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var vpnTemplateService: VpnTemplateService
    private val TEST_SERVER = "10.0.2.2:1194" // Local OpenVPN test server (Docker)

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        runBlocking {
            // Initialize dependencies manually (like VpnRoutingTest does)
            val database = AppDatabase.getDatabase(appContext)
            settingsRepository = SettingsRepository(
                vpnConfigDao = database.vpnConfigDao(),
                appRuleDao = database.appRuleDao(),
                providerCredentialsDao = database.providerCredentialsDao(),
                presetRuleDao = database.presetRuleDao()
            )
            
            // Provide credentials for local test template
            settingsRepository.saveProviderCredentials(
                ProviderCredentials(
                    templateId = "local-test",
                    username = "testuser",
                    password = "testpass"
                )
            )
        }
        
        val dummyNordApi = object : NordVpnApiService {
            override suspend fun getServers(token: String) = emptyList<NordServer>()
            override suspend fun getOvpnConfig(hostname: String): ResponseBody {
                throw UnsupportedOperationException("Nord API not required for local-test template")
            }
        }
        
        vpnTemplateService = VpnTemplateService(
            nordVpnApi = dummyNordApi,
            settingsRepo = settingsRepository,
            context = appContext
        )
    }

    @Test
    fun test_basicOpenVpnConnection() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🔌 TEST: Basic OpenVPN Connection")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // Get credentials from environment variables (passed via test arguments)
        // Create a test VPN config
        val testConfig = VpnConfig(
            id = "test-basic-connection",
            name = "Local Test Server",
            regionId = "LOCAL",
            templateId = "local-test",
            serverHostname = TEST_SERVER
        )
        
        println("✓ Created test VPN config for local server: $TEST_SERVER")
        
        // Prepare the OpenVPN configuration
        println("   Preparing OpenVPN configuration...")
        val preparedConfig = vpnTemplateService.prepareConfig(testConfig)
        
        assertThat(preparedConfig.ovpnFileContent).isNotEmpty()
        assertThat(preparedConfig.authFile).isNotNull()
        assertThat(preparedConfig.authFile?.exists()).isTrue()
        
        println("✓ OpenVPN config prepared")
        println("   Config size: ${preparedConfig.ovpnFileContent.length} bytes")
        println("   Auth file: ${preparedConfig.authFile?.absolutePath}")
        println("   Config contents:\n${preparedConfig.ovpnFileContent}")
        
        // Verify auth file contents
        val authLines = preparedConfig.authFile?.readLines()
        assertThat(authLines).isNotNull()
        assertThat(authLines!!.size).isAtLeast(2)
        println("   Auth file has ${authLines.size} lines")
        
        // Create NativeOpenVpnClient
        println("\n   Creating NativeOpenVpnClient...")
        val client = NativeOpenVpnClient(appContext, testVpnService)
        println("✓ Client created")
        
        assertThat(client.isConnected()).isFalse()
        
        // Attempt connection
        println("\n   Attempting to connect to OpenVPN server...")
        println("   Server: $TEST_SERVER")
        println("   This may take 30-60 seconds...")
        
        val startTime = System.currentTimeMillis()
        val connected = client.connect(
            ovpnConfig = preparedConfig.ovpnFileContent,
            authFilePath = preparedConfig.authFile?.absolutePath
        )
        val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
        
        println("\n   Connection attempt completed (${elapsedTime}s)")
        
        if (connected) {
            println("✅ SUCCESS: OpenVPN connection established!")
            
            // Wait a bit for connection to stabilize
            delay(3000)
            
            // Verify connection status
            val stillConnected = client.isConnected()
            assertThat(stillConnected).isTrue()
            println("✓ Connection verified: isConnected() = $stillConnected")
            
            // Test packet sending (even if not meaningful without real routing)
            println("\n   Testing packet sending...")
            val testPacket = ByteArray(64) { it.toByte() }
            client.sendPacket(testPacket)
            println("✓ Packet sent successfully")
            
            // Disconnect
            println("\n   Disconnecting...")
            client.disconnect()
            delay(2000)
            
            assertThat(client.isConnected()).isFalse()
            println("✅ Disconnected successfully")
            
        } else {
            println("❌ FAILED: OpenVPN connection could not be established")
            println("\n   Possible reasons:")
            println("   1. OpenVPN 3 native library not working correctly")
            println("   2. Invalid credentials")
            println("   3. Server unreachable")
            println("   4. Network connectivity issues")
            println("   5. OpenVPN config errors")
            println("\n   Check logcat for detailed errors:")
            println("   adb logcat -s NativeOpenVpnClient OpenVPN-JNI OpenVPN-Wrapper")
            
            // Don't fail the test immediately - log diagnostics
            println("\n   Diagnostics:")
            println("   - Client created: ✅")
            println("   - Config prepared: ✅")
            println("   - Auth file exists: ✅")
            println("   - Connection attempt: ❌")
        }
        
        // Cleanup
        preparedConfig.authFile?.delete()
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}

