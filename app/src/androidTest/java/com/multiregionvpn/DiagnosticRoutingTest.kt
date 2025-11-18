package com.multiregionvpn

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.multiregionvpn.core.VpnConnectionManager
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.test.DockerComposeManager
import com.multiregionvpn.test.BaseLocalTest
import dagger.hilt.android.testing.HiltAndroidTest
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

@HiltAndroidTest
class DiagnosticRoutingTest : BaseLocalTest() {

    private lateinit var ukConfig: VpnConfig
    private lateinit var frConfig: VpnConfig

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    override fun getComposeFile(): DockerComposeManager.ComposeFile = DockerComposeManager.ComposeFile.ROUTING

    @Before
    override fun setup() = runBlocking {
        super.setup()

        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🔬 DIAGNOSTIC TEST SETUP")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        stopVpnEngine()

        println("1️⃣ Clearing test data…")
        settingsRepo.clearAllAppRules()
        settingsRepo.clearAllVpnConfigs()
        println("✅ Test data cleared")

        println("2️⃣ Saving local-test credentials…")
        settingsRepo.saveProviderCredentials(ProviderCredentials("local-test", "testuser", "testpass"))
        println("✅ Credentials saved")

        val ukHost = DockerComposeManager.getVpnServerHostname(getComposeFile(), "UK", hostIp)
        val frHost = DockerComposeManager.getVpnServerHostname(getComposeFile(), "FR", hostIp)

        val ukHttpUrl = DockerComposeManager.getHttpServerUrl(getComposeFile(), "UK", hostIp)
        val frHttpUrl = DockerComposeManager.getHttpServerUrl(getComposeFile(), "FR", hostIp)

        assumeHttpReachable(ukHttpUrl, "UK HTTP server")
        assumeHttpReachable(frHttpUrl, "FR HTTP server")

        println("3️⃣ Creating VPN configs…")
        ukConfig = VpnConfig(
            id = "test-uk-diag",
            name = "Local UK Diagnostic",
            regionId = "UK",
            templateId = "local-test",
            serverHostname = ukHost
        )
        frConfig = VpnConfig(
            id = "test-fr-diag",
            name = "Local FR Diagnostic",
            regionId = "FR",
            templateId = "local-test",
            serverHostname = frHost
        )
        settingsRepo.saveVpnConfig(ukConfig)
        settingsRepo.saveVpnConfig(frConfig)
        println("   🇬🇧 Host: $ukHost")
        println("   🇫🇷 Host: $frHost")
        println("✅ VPN configs saved")

        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    @After
    override fun tearDown() = runBlocking {
        stopVpnEngine()
        super.tearDown()
    }

    @Test
    fun test_diagnostic_multiRouting() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 DIAGNOSTIC: Multi-route verification via dedicated client apps")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val ukHttpUrl = DockerComposeManager.getHttpServerUrl(getComposeFile(), "UK", hostIp)
        val frHttpUrl = DockerComposeManager.getHttpServerUrl(getComposeFile(), "FR", hostIp)

        println("Routing packages:")
        println("   🇬🇧 $UK_DIAGNOSTIC_PACKAGE → ${ukConfig.id} → $ukHttpUrl")
        println("   🇫🇷 $FR_DIAGNOSTIC_PACKAGE → ${frConfig.id} → $frHttpUrl")
        println("   🌐 $DIRECT_DIAGNOSTIC_PACKAGE → Direct internet (${DIRECT_DIAGNOSTIC_URL})")

        println("\n1️⃣ Creating app rules BEFORE VPN starts…")
        settingsRepo.createAppRule(UK_DIAGNOSTIC_PACKAGE, ukConfig.id)
        settingsRepo.createAppRule(FR_DIAGNOSTIC_PACKAGE, frConfig.id)
        delay(1000)
        val savedRules = settingsRepo.appRuleDao.getAllRulesList()
        println("   🧾 Current app rules in DB:")
        savedRules.forEach { rule ->
            println("      • ${rule.packageName} → ${rule.vpnConfigId}")
        }
        val ruleSummary = savedRules.joinToString { "${it.packageName}->${it.vpnConfigId}" }
        Log.i("DiagnosticTest", "App rules in DB (${savedRules.size}): $ruleSummary")
        println("✅ App rules stored")

        println("\n2️⃣ Starting VPN (should create two tunnels)…")
        startVpnEngine()

        println("\n3️⃣ Waiting for tunnels to become ready…")
        val connectionManager = VpnConnectionManager.getInstance()
        waitForTunnelReady(connectionManager, UK_TUNNEL_ID)
        waitForTunnelReady(connectionManager, FR_TUNNEL_ID)

        println("\n4️⃣ Executing diagnostic client checks…")
        val ukResult = runDiagnosticClient(UK_DIAGNOSTIC_PACKAGE, ukHttpUrl)
        println("   🇬🇧 $ukResult")
        assertTrue("UK diagnostic should hit SERVER_UK", ukResult.rawResponse?.contains("SERVER_UK") == true)

        val frResult = runDiagnosticClient(FR_DIAGNOSTIC_PACKAGE, frHttpUrl)
        println("   🇫🇷 $frResult")
        assertTrue("FR diagnostic should hit SERVER_FR", frResult.rawResponse?.contains("SERVER_FR") == true)

        val directResult = runDiagnosticClient(DIRECT_DIAGNOSTIC_PACKAGE, DIRECT_DIAGNOSTIC_URL)
        println("   🌐 $directResult")
        assertNotEquals("Direct diagnostic should avoid SERVER_UK", true, directResult.rawResponse?.contains("SERVER_UK"))
        assertNotEquals("Direct diagnostic should avoid SERVER_FR", true, directResult.rawResponse?.contains("SERVER_FR"))
        println("✅ Direct internet client bypassed VPN tunnels as expected")

        println("\n✅ Multi-route diagnostic completed")
    }

    private suspend fun waitForTunnelReady(
        manager: VpnConnectionManager,
        tunnelId: String,
        timeoutSeconds: Int = 60
    ) {
        var elapsed = 0
        while (elapsed < timeoutSeconds) {
            if (manager.isTunnelReadyForRouting(tunnelId)) {
                println("   ✅ Tunnel $tunnelId ready at ${elapsed}s")
                return
            }
            if (elapsed % 5 == 0) {
                println("   ⏳ Waiting for tunnel $tunnelId… (${elapsed}s)")
            }
            delay(1000)
            elapsed++
        }
        throw AssertionError("Tunnel $tunnelId didn't connect within $timeoutSeconds seconds")
    }

    private suspend fun runDiagnosticClient(
        packageName: String,
        url: String
    ): DiagnosticResult = withTimeout(30_000) {
        suspendCancellableCoroutine { continuation ->
            val context = instrumentation.targetContext
            val resultAction = "$ACTION_DIAGNOSTIC_RESULT.$packageName.${SystemClock.elapsedRealtime()}"
            val resultPackage = context.packageName
            val intentFilter = IntentFilter(resultAction)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (!continuation.isActive) return
                    val extras = intent?.extras ?: Bundle()
                    val resultCode = intent?.getIntExtra(KEY_RESULT_CODE, Activity.RESULT_CANCELED)
                        ?: Activity.RESULT_CANCELED
                    val pkg = extras.getString(KEY_PACKAGE_NAME) ?: packageName
                    val result = DiagnosticResult(
                        packageName = pkg,
                        responseCode = extras.getInt(KEY_RESPONSE_CODE, -1),
                        responseMessage = extras.getString(KEY_RESPONSE_MESSAGE),
                        countryCode = extras.getString(KEY_COUNTRY_CODE),
                        rawResponse = extras.getString(KEY_RAW_RESPONSE),
                        url = extras.getString(KEY_URL) ?: url,
                        contentLength = extras.getInt(KEY_CONTENT_LENGTH, -1),
                        durationMs = extras.getLong(KEY_DURATION_MS, -1),
                        remoteIp = extras.getString(KEY_REMOTE_IP),
                        error = extras.getString(KEY_ERROR),
                        errorClass = extras.getString(KEY_ERROR_CLASS)
                    )
                    if (resultCode == Activity.RESULT_OK && result.error == null) {
                        continuation.resume(result)
                    } else {
                        continuation.resumeWithException(IllegalStateException(result.toString()))
                    }
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, intentFilter)
            }

            continuation.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }

            val intent = Intent(ACTION_RUN_DIAGNOSTIC)
                .setPackage(packageName)
                .putExtra(EXTRA_RESULT_ACTION, resultAction)
                .putExtra(EXTRA_RESULT_PACKAGE, resultPackage)
                .putExtra(EXTRA_URL, url)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)

            context.sendBroadcast(intent)
        }
    }

    private fun assumeHttpReachable(urlString: String, label: String) {
        val reachable = try {
            val url = URL(urlString)
            val host = url.host
            if (host.startsWith("10.")) {
                println("   ℹ️  Skipping reachability check for $label ($urlString) - requires VPN tunnel")
                return
            }
            val port = if (url.port != -1) url.port else url.defaultPort
            Socket().use { socket ->
                socket.connect(InetSocketAddress(url.host, port), 2_000)
            }
            true
        } catch (t: Throwable) {
            println("   ⚠️  Could not reach $label at $urlString: ${t.message}")
            false
        }
        assumeTrue(
            "$label not reachable at $urlString. Start docker-compose -f ${getComposeFile().fileName} up -d before running this test.",
            reachable
        )
    }

    private data class DiagnosticResult(
        val packageName: String,
        val responseCode: Int,
        val responseMessage: String?,
        val countryCode: String?,
        val rawResponse: String?,
        val url: String,
        val contentLength: Int,
        val durationMs: Long,
        val remoteIp: String?,
        val error: String?,
        val errorClass: String?
    ) {
        override fun toString(): String = buildString {
            append("DiagnosticResult(package=")
            append(packageName)
            append(", code=")
            append(responseCode)
            responseMessage?.let { append(" ").append(it) }
            append(", url=")
            append(url)
            append(", durationMs=")
            append(durationMs)
            append(", remoteIp=")
            append(remoteIp ?: "?" )
            if (rawResponse != null) {
                append(", body=")
                append(rawResponse.take(32))
            }
            if (error != null) {
                append(", error=")
                append(error)
                errorClass?.let { append(" (").append(it).append(")") }
            }
            append(")")
        }
    }

    companion object {
        private const val ACTION_RUN_DIAGNOSTIC = "com.example.diagnostic.ACTION_RUN"
        private const val EXTRA_RESULT_RECEIVER = "com.example.diagnostic.EXTRA_RESULT_RECEIVER"
        private const val EXTRA_URL = "com.example.diagnostic.EXTRA_URL"
        private const val EXTRA_RESULT_ACTION = "com.example.diagnostic.EXTRA_RESULT_ACTION"
        private const val EXTRA_RESULT_PACKAGE = "com.example.diagnostic.EXTRA_RESULT_PACKAGE"
        private const val KEY_RESPONSE_CODE = "responseCode"
        private const val KEY_RESPONSE_MESSAGE = "responseMessage"
        private const val KEY_COUNTRY_CODE = "countryCode"
        private const val KEY_RAW_RESPONSE = "rawResponse"
        private const val KEY_ERROR = "error"
        private const val KEY_ERROR_CLASS = "errorClass"
        private const val KEY_PACKAGE_NAME = "packageName"
        private const val KEY_URL = "url"
        private const val KEY_CONTENT_LENGTH = "contentLength"
        private const val KEY_DURATION_MS = "durationMs"
        private const val KEY_REMOTE_IP = "remoteIp"
        private const val KEY_RESULT_CODE = "resultCode"
        private const val ACTION_DIAGNOSTIC_RESULT = "com.multiregionvpn.DIAGNOSTIC_RESULT"

        private const val UK_DIAGNOSTIC_PACKAGE = "com.example.diagnostic.uk"
        private const val FR_DIAGNOSTIC_PACKAGE = "com.example.diagnostic.fr"
        private const val DIRECT_DIAGNOSTIC_PACKAGE = "com.example.diagnostic.direct"
        private const val UK_TUNNEL_ID = "local-test_UK"
        private const val FR_TUNNEL_ID = "local-test_FR"
        private const val DIRECT_DIAGNOSTIC_URL = "https://example.com"
    }
}
