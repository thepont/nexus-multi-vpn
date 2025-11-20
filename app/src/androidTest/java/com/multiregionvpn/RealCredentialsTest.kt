package com.multiregionvpn

import android.content.Context
import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.multiregionvpn.core.vpnclient.AuthenticationException
import com.multiregionvpn.core.vpnclient.NativeOpenVpnClient
import com.multiregionvpn.core.VpnTemplateService
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.network.NordVpnApiService
import com.google.common.truth.Truth.assertThat
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Test to verify if real NordVPN credentials from .env work or fail.
 */
@RunWith(AndroidJUnit4::class)
class RealCredentialsTest {

    private lateinit var appContext: Context
    
    @Mock
    private lateinit var mockVpnService: VpnService
    
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var vpnTemplateService: VpnTemplateService
    private val TEST_SERVER = "uk1234.nordvpn.com" // Real NordVPN server

    @Before
    fun setup() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        MockitoAnnotations.openMocks(this)
        
        // Initialize dependencies
        val database = androidx.room.Room.databaseBuilder(
            appContext.applicationContext,
            AppDatabase::class.java,
            "region_router_db"
        )
            .addCallback(AppDatabase.PresetRuleCallback())
            .build()
        settingsRepository = SettingsRepository(
            vpnConfigDao = database.vpnConfigDao(),
            appRuleDao = database.appRuleDao(),
            providerCredentialsDao = database.providerCredentialsDao(),
            presetRuleDao = database.presetRuleDao()
        )
        
        // Create VpnTemplateService
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.nordvpn.com/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(
                Moshi.Builder().build()
            ))
            .build()
        
        val nordVpnApiService: NordVpnApiService = retrofit.create(NordVpnApiService::class.java)
        
        vpnTemplateService = VpnTemplateService(
            nordVpnApi = nordVpnApiService,
            settingsRepo = settingsRepository,
            context = appContext
        )
    }

    @Test
    fun test_realCredentials_fromEnv() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🔐 TEST: Real Credentials from .env")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // Get credentials from environment variables (passed via test arguments)
        val username = getTestArgument("NORDVPN_USERNAME")
        val password = getTestArgument("NORDVPN_PASSWORD")
        
        if (username == null || password == null) {
            println("⚠️ Skipping test - credentials not provided")
            println("   Set NORDVPN_USERNAME and NORDVPN_PASSWORD as test arguments")
            return@runBlocking
        }
        
        println("✓ Credentials loaded")
        println("   Username: ${username.take(5)}*** (length: ${username.length})")
        println("   Password: ${password.take(3)}*** (length: ${password.length})")
        
        // Save credentials to database (simulating user input)
        val providerCreds = com.multiregionvpn.data.database.ProviderCredentials(
            templateId = "nordvpn",
            username = username,
            password = password
        )
        settingsRepository.saveProviderCredentials(providerCreds)
        println("✓ Credentials saved to database")
        
        // Create a test VPN config
        val testConfig = VpnConfig(
            id = "test-real-creds",
            name = "Test UK Server",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = TEST_SERVER
        )
        settingsRepository.saveVpnConfig(testConfig)
        println("✓ VPN config created: $TEST_SERVER")
        
        // Prepare the OpenVPN configuration using VpnTemplateService
        println("\n   Preparing OpenVPN configuration...")
        val preparedConfig = try {
            vpnTemplateService.prepareConfig(testConfig)
        } catch (e: Exception) {
            println("   ❌ Failed to prepare config: ${e.message}")
            println("   This might indicate:")
            println("     1. Network connectivity issue")
            println("     2. NordVPN API endpoint changed")
            println("     3. HTTP 403 (authentication required for API)")
            throw e
        }
        
        assertThat(preparedConfig.ovpnFileContent).isNotEmpty()
        assertThat(preparedConfig.authFile).isNotNull()
        assertThat(preparedConfig.authFile?.exists()).isTrue()
        
        println("✓ OpenVPN config prepared")
        println("   Config size: ${preparedConfig.ovpnFileContent.length} bytes")
        println("   Auth file: ${preparedConfig.authFile?.name}")
        
        // Verify auth file contents match what we saved
        val authLines = preparedConfig.authFile?.readLines()
        assertThat(authLines).isNotNull()
        assertThat(authLines!!.size).isAtLeast(2)
        assertThat(authLines[0].trim()).isEqualTo(username)
        assertThat(authLines[1].trim()).isEqualTo(password)
        println("✓ Auth file contains correct credentials")
        
        // Create NativeOpenVpnClient
        println("\n   Creating NativeOpenVpnClient...")
        val client = NativeOpenVpnClient(appContext, mockVpnService)
        println("✓ Client created")
        
        assertThat(client.isConnected()).isFalse()
        
        // Attempt connection with REAL credentials
        println("\n   Attempting to connect to OpenVPN server...")
        println("   Server: $TEST_SERVER")
        println("   Using REAL credentials from .env")
        println("   This may take 30-60 seconds...")
        
        val startTime = System.currentTimeMillis()
        
        try {
            val connected = client.connect(
                ovpnConfig = preparedConfig.ovpnFileContent,
                authFilePath = preparedConfig.authFile?.absolutePath
            )
            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000
            
            println("\n   Connection attempt completed (${elapsedTime}s)")
            
            if (connected) {
                println("✅ SUCCESS: Connection established with real credentials!")
                
                // Wait a bit for connection to stabilize
                delay(3000)
                
                // Verify connection status
                val stillConnected = client.isConnected()
                assertThat(stillConnected).isTrue()
                println("✓ Connection verified: isConnected() = $stillConnected")
                
                // Disconnect
                println("\n   Disconnecting...")
                client.disconnect()
                delay(2000)
                
                assertThat(client.isConnected()).isFalse()
                println("✅ Disconnected successfully")
                
            } else {
                println("❌ FAILED: Connection did not establish")
                val errorMsg = client.getLastError()
                println("   Error message: ${errorMsg ?: "No error message available"}")
                
                // Check if it's an auth error
                if (errorMsg != null) {
                    val lowerError = errorMsg.lowercase()
                    val isAuthError = lowerError.contains("auth") || 
                                     lowerError.contains("credential") ||
                                     lowerError.contains("password") ||
                                     lowerError.contains("username") ||
                                     lowerError.contains("invalid")
                    
                    if (isAuthError) {
                        println("\n   🔐 AUTHENTICATION FAILURE DETECTED!")
                        println("   Your credentials appear to be invalid or expired")
                        println("   Check:")
                        println("     1. Username/password are correct")
                        println("     2. Service credentials (not access token) are used")
                        println("     3. Account is active")
                    } else {
                        println("\n   ⚠️  Connection failed for non-auth reason")
                        println("   Possible causes:")
                        println("     1. Network connectivity issues")
                        println("     2. Server unreachable")
                        println("     3. OpenVPN 3 not fully configured")
                        println("     4. Config parsing errors")
                    }
                }
                
                println("\n   Diagnostics:")
                println("   - Config prepared: ✅")
                println("   - Auth file exists: ✅")
                println("   - Credentials in file: ✅")
                println("   - Connection attempt: ❌")
            }
            
        } catch (e: AuthenticationException) {
            println("\n   🔐 AUTHENTICATION EXCEPTION THROWN!")
            println("   Error: ${e.message}")
            println("\n   Your credentials failed authentication!")
            println("   This means:")
            println("     1. Username/password are incorrect, OR")
            println("     2. Credentials have expired, OR")
            println("     3. Account is suspended/inactive")
            println("\n   Action needed: Update credentials in .env file")
        } catch (e: Exception) {
            println("\n   ❌ Exception during connection: ${e.javaClass.simpleName}")
            println("   Error: ${e.message}")
            e.printStackTrace()
        } finally {
            // Cleanup
            preparedConfig.authFile?.delete()
        }
        
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    private fun getTestArgument(key: String): String? {
        return try {
            val bundle = InstrumentationRegistry.getArguments()
            bundle.getString(key)
        } catch (e: Exception) {
            null
        }
    }
}

