package com.multiregionvpn.core

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockNordVpnApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var service: VpnTemplateService

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockNordVpnApi = mock(NordVpnApiService::class.java)
        mockSettingsRepo = mock(SettingsRepository::class.java)

        `when`(mockContext.cacheDir).thenReturn(tempFolder.root)

        service = VpnTemplateService(
            nordVpnApi = mockNordVpnApi,
            settingsRepo = mockSettingsRepo,
            context = mockContext
        )
    }

    @Test
    fun prepareLocalTestConfig_createsSecureAuthFileWithCorrectContent() = runBlocking {
        val config = VpnConfig(
            id = "test_tunnel_1",
            name = "Local Test Tunnel",
            regionId = "UK",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1194"
        )
        val creds = ProviderCredentials(
            templateId = "local-test",
            username = "testuser",
            password = "testpassword"
        )

        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(creds)

        val prepared = service.prepareLocalTestConfigForTest(config)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText()).isEqualTo("testuser\ntestpassword\n")

        // Check file permissions are restricted
        assertThat(authFile.canRead()).isTrue()
        assertThat(authFile.canWrite()).isTrue()
        assertThat(authFile.canExecute()).isFalse()
    }

    @Test
    fun prepareNordVpnConfig_createsSecureAuthFileWithCorrectContent() = runBlocking {
        val config = VpnConfig(
            id = "nord_tunnel_1",
            name = "NordVPN UK",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk123"
        )
        val creds = ProviderCredentials(
            templateId = "nordvpn",
            username = "norduser",
            password = "nordpassword"
        )

        `when`(mockSettingsRepo.getProviderCredentials("nordvpn")).thenReturn(creds)
        `when`(mockNordVpnApi.getOvpnConfig(anyString() ?: ""))
            .thenReturn("client\ndev tun\nauth-user-pass\n".toResponseBody())

        val prepared = service.prepareConfig(config)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText()).isEqualTo("norduser\nnordpassword\n")
        assertThat(prepared.ovpnFileContent).contains("auth-user-pass ${authFile.absolutePath}")

        // Check file permissions are restricted
        assertThat(authFile.canRead()).isTrue()
        assertThat(authFile.canWrite()).isTrue()
        assertThat(authFile.canExecute()).isFalse()
    }

    // Helper extension to access private function or test via prepareConfig
    private suspend fun VpnTemplateService.prepareLocalTestConfigForTest(config: VpnConfig) =
        prepareConfig(config)
}
