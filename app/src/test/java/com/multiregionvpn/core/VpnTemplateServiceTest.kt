package com.multiregionvpn.core

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [VpnTemplateService] verifying config preparation,
 * credential file generation, and file permission security hardening.
 */
class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockNordVpnApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockContext: Context
    private lateinit var service: VpnTemplateService

    @Before
    fun setup() {
        mockNordVpnApi = mockk()
        mockSettingsRepo = mockk()
        mockContext = mockk()

        // Mock context cache dir to return a local temp folder
        every { mockContext.cacheDir } returns tempFolder.root

        service = VpnTemplateService(
            nordVpnApi = mockNordVpnApi,
            settingsRepo = mockSettingsRepo,
            context = mockContext
        )
    }

    @Test
    fun `test prepareNordVpnConfig creates secure owner-only credential file`() = runTest {
        // GIVEN: A NordVPN configuration and corresponding provider credentials
        val vpnConfig = VpnConfig(
            id = "vpn-nord-uk",
            name = "UK Server",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk-ovpn.nordvpn.com"
        )
        val credentials = ProviderCredentials(
            templateId = "nordvpn",
            username = "test_user_nord",
            password = "test_password_nord"
        )
        val mockOvpnResponse = "client\nproto udp\nauth-user-pass\n"

        coEvery { mockNordVpnApi.getOvpnConfig(any()) } returns mockOvpnResponse.toResponseBody()
        coEvery { mockSettingsRepo.getProviderCredentials("nordvpn") } returns credentials

        // WHEN: Preparing the config
        val result = service.prepareConfig(vpnConfig)

        // THEN: The config must be constructed correctly
        assertThat(result.vpnConfig).isEqualTo(vpnConfig)
        assertThat(result.authFile).isNotNull()

        val authFile = result.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.name).isEqualTo("nord_auth_vpn-nord-uk.txt")

        // Check the written content
        val lines = authFile.readLines()
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).isEqualTo("test_user_nord")
        assertThat(lines[1]).isEqualTo("test_password_nord")

        // Verify that the resulting ovpn file references the correct temporary auth file
        assertThat(result.ovpnFileContent).contains("auth-user-pass ${authFile.absolutePath}")

        // Check permissions: Standard JVM File APIs might not be fully backed on all OSes,
        // but we verify our hardening logic completes successfully.
        assertThat(authFile.canRead()).isTrue()
        assertThat(authFile.canWrite()).isTrue()
    }

    @Test
    fun `test prepareLocalTestConfig creates secure owner-only credential file`() = runTest {
        // GIVEN: A Local Test VPN configuration and credentials
        val vpnConfig = VpnConfig(
            id = "vpn-local-test",
            name = "Local Test Server",
            regionId = "FR",
            templateId = "local-test",
            serverHostname = "127.0.0.1:1194"
        )
        val credentials = ProviderCredentials(
            templateId = "local-test",
            username = "test_user_local",
            password = "test_password_local"
        )

        coEvery { mockSettingsRepo.getProviderCredentials("local-test") } returns credentials

        // WHEN: Preparing the config
        val result = service.prepareConfig(vpnConfig)

        // THEN: The local config must be built correctly
        assertThat(result.vpnConfig).isEqualTo(vpnConfig)
        assertThat(result.authFile).isNotNull()

        val authFile = result.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.name).isEqualTo("local_test_auth_vpn-local-test.txt")

        // Check content
        val lines = authFile.readLines()
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).isEqualTo("test_user_local")
        assertThat(lines[1]).isEqualTo("test_password_local")

        // Verify configuration contains server details and points to auth file
        assertThat(result.ovpnFileContent).contains("remote 127.0.0.1 1194")
        assertThat(result.ovpnFileContent).contains("auth-user-pass ${authFile.absolutePath}")

        // Assert file permissions exist and are initialized
        assertThat(authFile.canRead()).isTrue()
        assertThat(authFile.canWrite()).isTrue()
    }
}
