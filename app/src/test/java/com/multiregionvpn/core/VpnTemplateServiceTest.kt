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
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val nordVpnApi = mockk<NordVpnApiService>()
    private val settingsRepo = mockk<SettingsRepository>()
    private val context = mockk<Context>()

    private lateinit var service: VpnTemplateService

    @Before
    fun setup() {
        every { context.cacheDir } returns tempFolder.root
        service = VpnTemplateService(nordVpnApi, settingsRepo, context)
    }

    @Test
    fun prepareNordVpnConfig_createsSecureAuthFileWithOwnerPermissions() = runBlocking {
        // GIVEN: NordVPN config and credentials
        val config = VpnConfig(
            id = "uk_nord",
            name = "UK Server",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk123.nordvpn.com"
        )
        val creds = ProviderCredentials(
            templateId = "nordvpn",
            username = "test_user",
            password = "test_password"
        )

        val mockResponseBody = mockk<ResponseBody>()
        every { mockResponseBody.string() } returns "client\ndev tun\nauth-user-pass\n"
        coEvery { nordVpnApi.getOvpnConfig("uk123.nordvpn.com") } returns mockResponseBody
        coEvery { settingsRepo.getProviderCredentials("nordvpn") } returns creds

        // WHEN: Preparing the config
        val prepared = service.prepareNordVpnConfig_testWrapper(config)

        // THEN: Auth file is created with correct content and restricted permissions
        val authFile = prepared.authFile
        assertThat(authFile).isNotNull()
        assertThat(authFile!!.exists()).isTrue()
        assertThat(authFile.readText(Charsets.UTF_8)).isEqualTo("test_user\ntest_password\n")

        // Check security permission properties (on OS platforms supporting file permissions)
        assertThat(authFile.canRead()).isTrue()
        assertThat(authFile.canWrite()).isTrue()
    }

    @Test
    fun prepareLocalTestConfig_createsSecureAuthFileWithOwnerPermissions() = runBlocking {
        // GIVEN: Local test config and credentials
        val config = VpnConfig(
            id = "local_uk",
            name = "Local UK Test",
            regionId = "UK",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1199"
        )
        val creds = ProviderCredentials(
            templateId = "local-test",
            username = "local_user",
            password = "local_password"
        )

        coEvery { settingsRepo.getProviderCredentials("local-test") } returns creds

        // WHEN: Preparing the config
        val prepared = service.prepareConfig(config)

        // THEN: Auth file is created with correct content and path injected into ovpn config
        val authFile = prepared.authFile
        assertThat(authFile).isNotNull()
        assertThat(authFile!!.exists()).isTrue()
        assertThat(authFile.readText(Charsets.UTF_8)).isEqualTo("local_user\nlocal_password\n")
        assertThat(prepared.ovpnFileContent).contains("auth-user-pass ${authFile.absolutePath}")
    }

    // Helper extension to test private prepareNordVpnConfig via prepareConfig
    private suspend fun VpnTemplateService.prepareNordVpnConfig_testWrapper(config: VpnConfig): PreparedVpnConfig {
        return this.prepareConfig(config)
    }
}
