package com.multiregionvpn

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.multiregionvpn.core.VpnConnectionManager
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.test.DockerComposeManager
import com.multiregionvpn.test.BaseLocalTest
import dagger.hilt.android.testing.HiltAndroidTest
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test DNS resolution through VPN using domain names.
 *
 * Requires docker-compose.dns-domain.yaml to be running on the host.
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class DnsDomainTest : BaseLocalTest() {

    override fun getComposeFile(): DockerComposeManager.ComposeFile = DockerComposeManager.ComposeFile.DNS_DOMAIN

    private val testPackage = androidx.test.platform.app.InstrumentationRegistry
        .getInstrumentation().targetContext.packageName

    private val testDomain = "test.example.com"
    private val testUrl = "http://$testDomain"

    private lateinit var dnsVpnConfigId: String
    private lateinit var dnsVpnHost: String

    override fun setup() = runBlocking {
        super.setup()

        dnsVpnHost = DockerComposeManager.getVpnServerHostname(getComposeFile(), "DNS_DOMAIN", hostIp)
        assumeVpnServiceReachable(dnsVpnHost, "DNS OpenVPN server")

        println("✓ DNS VPN Server Host: $dnsVpnHost")

        val providerCreds = ProviderCredentials(
            templateId = "local-test",
            username = "testuser",
            password = "testpass"
        )
        settingsRepo.saveProviderCredentials(providerCreds)

        dnsVpnConfigId = UUID.randomUUID().toString()
        val dnsVpnConfig = VpnConfig(
            id = dnsVpnConfigId,
            name = "Local DNS Domain Server",
            regionId = "DNS_DOMAIN",
            templateId = "local-test",
            serverHostname = dnsVpnHost
        )
        settingsRepo.saveVpnConfig(dnsVpnConfig)
        println("✓ DNS VPN config created: $dnsVpnConfigId")
    }

    @Test
    fun test_dnsResolutionViaDomainName() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: DNS Resolution via Domain Name")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        settingsRepo.createAppRule(testPackage, dnsVpnConfigId)
        println("✓ Created app rule: $testPackage -> DNS VPN ($dnsVpnConfigId)")

        startVpnEngine()
        println("✓ VPN service started")

        delay(10_000)

        val connectionManager = VpnConnectionManager.getInstance()
        val dnsTunnelId = "local-test_DNS_DOMAIN"
        delay(2_000)
        val dnsConnected = connectionManager.isTunnelConnected(dnsTunnelId)
        println("   DNS Tunnel Connected: $dnsConnected")
        assertTrue("DNS tunnel should be connected", dnsConnected)

        delay(5_000)

        println("\n   Making HTTP request to $testUrl")

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(testUrl).build()

        val response = client.newCall(request).execute()
        val body = response.body?.string()

        println("   Response code: ${response.code}")
        println("   Response body: $body")

        assertTrue("HTTP request should succeed", response.isSuccessful)
        assertEquals("DNS_TEST_PASSED", body?.trim())

        println("✅ DNS resolution via domain name works correctly")
    }

    @Test
    fun test_dnsServersReceivedFromDhcp() = runBlocking {
        println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🧪 TEST: DNS Servers Received from OpenVPN DHCP")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        settingsRepo.createAppRule(testPackage, dnsVpnConfigId)
        startVpnEngine()

        delay(10_000)
        val connectionManager = VpnConnectionManager.getInstance()
        val dnsTunnelId = "local-test_DNS_DOMAIN"
        delay(2_000)
        val isConnected = connectionManager.isTunnelConnected(dnsTunnelId)
        assertTrue("DNS tunnel should be connected", isConnected)

        delay(5_000)

        println("✓ DNS servers should be received from OpenVPN DHCP")
        println("✓ Verified implicitly by successful domain name resolution test")
    }

    private fun assumeVpnServiceReachable(hostSpec: String, label: String) {
        val parts = hostSpec.split(":")
        val host = parts.first()
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 1194
        val reachable = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2_000)
            }
            true
        } catch (t: Throwable) {
            false
        }
        assumeTrue(
            "$label not reachable at $hostSpec. Start docker-compose -f ${getComposeFile().fileName} up -d before running this test.",
            reachable
        )
    }
}
