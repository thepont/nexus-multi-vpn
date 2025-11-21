import android.content.Context
import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.multiregionvpn.core.vpnclient.NativeOpenVpnClient
import com.multiregionvpn.core.VpnTemplateService
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.network.NordVpnApiService
import com.google.common.truth.Truth.assertThat
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.After // Import After

import org.mockito.kotlin.whenever
import org.mockito.ArgumentMatchers.anyString
import okhttp3.ResponseBody
import android.os.ParcelFileDescriptor
import android.content.Intent
import android.os.IBinder

// Create a dummy VpnService implementation for testing purposes
// This class extends VpnService but doesn't implement any actual VPN logic.
// It exists purely to satisfy the NativeOpenVpnClient constructor's VpnService requirement
// and to allow a non-null VpnService object to be passed.
class TestVpnService : VpnService() {
    override fun onCreate() {
        super.onCreate()
        // No actual VPN setup needed for this test
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not binding to any service
    }
}

/**
 * Basic connection test to verify OpenVPN 3 native client can connect to a VPN server.
 * This test focuses solely on the connection itself, not routing.
 */
@RunWith(AndroidJUnit4::class)
class BasicConnectionTest {

    private lateinit var appContext: Context
    
    // private lateinit var mockVpnService: VpnService // Removed
    private lateinit var testVpnService: TestVpnService // Use actual VpnService for NativeClient

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var vpnTemplateService: VpnTemplateService
    private val TEST_SERVER = "uk1234.nordvpn.com" // Use a real NordVPN server

    @Mock
    private lateinit var mockNordVpnApiService: NordVpnApiService
    
    private var tunFd: Int = -1
    private lateinit var pfd: ParcelFileDescriptor

    @Before
    fun setup() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        MockitoAnnotations.openMocks(this)
        
        // Initialize a dummy VpnService instance
        testVpnService = TestVpnService()
        // Attach to context needed for VpnService internal operations
        // The VpnService context is usually an Application context.
        // For testing, we can use the targetContext.
        testVpnService.attach(appContext, null, null, null, null) 

        // Create a dummy TUN file descriptor using a pipe
        // This provides a valid FD for the native code without creating a real TUN device
        val pipe = ParcelFileDescriptor.createPipe()
        pfd = pipe[0] // Use the read end of the pipe as the TUN FD
        tunFd = pfd.fd

        // Initialize dependencies manually
        val database = AppDatabase.getDatabase(appContext)
        settingsRepository = SettingsRepository(
            vpnConfigDao = database.vpnConfigDao(),
            appRuleDao = database.appRuleDao(),
            providerCredentialsDao = database.providerCredentialsDao(),
            presetRuleDao = database.presetRuleDao()
        )
        
        // Use mocked API service
        vpnTemplateService = VpnTemplateService(
            nordVpnApi = mockNordVpnApiService,
            settingsRepo = settingsRepository,
            context = appContext
        )
    }

    @After
    fun teardown() {
        // Close the ParcelFileDescriptor to prevent resource leaks
        if (tunFd != -1) {
            pfd.close()
            // The write end of the pipe might also need to be closed if it's held elsewhere
            // For now, assume pfd handles both ends or garbage collection will clean up
        }
        testVpnService.onDestroy() // Clean up the dummy VpnService
    }

    @Test
    fun test_basicOpenVpnConnection() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🔌 TEST: Basic OpenVPN Connection")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // Get credentials from environment variables (passed via test arguments)
        println("   Loading credentials...")
        val username = getTestArgument("NORDVPN_USERNAME")
        val password = getTestArgument("NORDVPN_PASSWORD")
        
        if (username == null || password == null) {
            println("⚠️ Skipping test - credentials not provided")
            println("   Set NORDVPN_USERNAME and NORDVPN_PASSWORD as test arguments")
            return@runBlocking
        }
        
        println("✓ Credentials loaded (username length: ${username.length})")
        
        // Create a test VPN config
        println("   Creating test VPN config...")
        val testConfig = VpnConfig(
            id = "test-basic-connection",
            name = "Test UK Server",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = TEST_SERVER
        )
        
        println("✓ Created test VPN config: $TEST_SERVER")
        
        // Stub the API call to return a dummy config
        // This avoids network dependency on api.nordvpn.com
        println("   Stubbing NordVpnApiService.getOvpnConfig...")
        val dummyConfig = """
            client
            dev tun
            proto udp
            remote $TEST_SERVER 1194
            resolv-retry infinite
            nobind
            persist-key
            persist-tun
            auth-user-pass
            verb 3
        """.trimIndent()
        
        whenever(mockNordVpnApiService.getOvpnConfig(anyString())).thenReturn(
            ResponseBody.create(null, dummyConfig)
        )
        println("✓ Stubbed NordVpnApiService.getOvpnConfig")
        
        // Prepare the OpenVPN configuration
        println("   Preparing OpenVPN configuration...")
        val preparedConfig = vpnTemplateService.prepareConfig(testConfig)
        
        assertThat(preparedConfig.ovpnFileContent).isNotEmpty()
        assertThat(preparedConfig.authFile).isNotNull()
        assertThat(preparedConfig.authFile?.exists()).isTrue()
        
        println("✓ OpenVPN config prepared")
        println("   Config size: ${preparedConfig.ovpnFileContent.length} bytes")
        println("   Auth file: ${preparedConfig.authFile?.absolutePath}")
        
        // Verify auth file contents
        val authLines = preparedConfig.authFile?.readLines()
        assertThat(authLines).isNotNull()
        assertThat(authLines!!.size).isAtLeast(2)
        println("   Auth file has ${authLines.size} lines")
        
        // Create NativeOpenVpnClient
        println("\n   Creating NativeOpenVpnClient...")
        val client = NativeOpenVpnClient(appContext, testVpnService, tunFd, "test-basic-connection", null, null)
        println("✓ Client created")
        
        assertThat(client.isConnected()).isFalse()
        
        // Attempt connection
        println("\n   Attempting to connect to OpenVPN server...")
        println("   Server: $TEST_SERVER")
        println("   This may take 60 seconds...")

        val startTime = System.currentTimeMillis()
        println("   Calling client.connect() at $startTime")
        client.connect(
            ovpnConfig = preparedConfig.ovpnFileContent,
            authFilePath = preparedConfig.authFile?.absolutePath
        )

        var connected = false
        val timeout = System.currentTimeMillis() + 60000 // 60 seconds timeout
        var isConnectedResult = false
        while (System.currentTimeMillis() < timeout) {
            isConnectedResult = client.isConnected()
            println("   Polling isConnected(): $isConnectedResult")
            if (isConnectedResult) {
                connected = true
                break
            }
            delay(1000)
        }
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

        assertThat(connected).isTrue()
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