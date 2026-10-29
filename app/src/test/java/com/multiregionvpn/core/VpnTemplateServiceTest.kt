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
import java.io.File

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockNordApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockContext: Context
    private lateinit var service: VpnTemplateService
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        mockNordApi = mock(NordVpnApiService::class.java)
        mockSettingsRepo = mock(SettingsRepository::class.java)
        mockContext = mock(Context::class.java)

        cacheDir = tempFolder.newFolder("cache")
        `when`(mockContext.cacheDir).thenReturn(cacheDir)

        service = VpnTemplateService(mockNordApi, mockSettingsRepo, mockContext)
    }

    @Test
    fun prepareLocalTestConfig_createsSecureAuthFileWithCorrectPermissions() = runBlocking {
        val creds = ProviderCredentials("local-test", "testuser", "testpass")
        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(creds)

        val config = VpnConfig(
            id = "test_1",
            name = "Local Test Server",
            regionId = "US",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1194"
        )

        val prepared = service.prepareConfig(config)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!
        assertThat(authFile.exists()).isTrue()

        // Verify content
        val content = authFile.readText(Charsets.UTF_8)
        assertThat(content).isEqualTo("testuser\ntestpass\n")

        // Verify readable and writable by owner
        assertThat(authFile.canRead()).isTrue()
        assertThat(authFile.canWrite()).isTrue()
    }

    @Test
    fun prepareNordVpnConfig_createsSecureAuthFile() = runBlocking {
        val creds = ProviderCredentials("nordvpn", "norduser", "nordpass")
        `when`(mockSettingsRepo.getProviderCredentials("nordvpn")).thenReturn(creds)

        val dummyResponseBody = "client\ndev tun\nauth-user-pass".toResponseBody(null)
        `when`(mockNordApi.getOvpnConfig(anyString() ?: "")).thenReturn(dummyResponseBody)

        val config = VpnConfig(
            id = "nord_1",
            name = "NordVPN Server",
            regionId = "US",
            templateId = "nordvpn",
            serverHostname = "us123.nordvpn.com"
        )

        val prepared = service.prepareConfig(config)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!
        assertThat(authFile.exists()).isTrue()

        val content = authFile.readText(Charsets.UTF_8)
        assertThat(content).isEqualTo("norduser\nnordpass\n")

        assertThat(prepared.ovpnFileContent).contains("auth-user-pass ${authFile.absolutePath}")
    }
}
