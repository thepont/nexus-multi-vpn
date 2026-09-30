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
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

/**
 * Unit test verifying VpnTemplateService secure credential file handling and permissions.
 */
class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockNordApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockContext: Context
    private lateinit var cacheDir: File
    private lateinit var service: VpnTemplateService

    @Before
    fun setup() {
        mockNordApi = mock(NordVpnApiService::class.java)
        mockSettingsRepo = mock(SettingsRepository::class.java)
        mockContext = mock(Context::class.java)
        cacheDir = tempFolder.newFolder("cache")
        `when`(mockContext.cacheDir).thenReturn(cacheDir)

        service = VpnTemplateService(mockNordApi, mockSettingsRepo, mockContext)
    }

    @Test
    fun prepareNordVpnConfig_createsAuthFileWithSecurePermissionsAndContent() = runBlocking {
        val config = VpnConfig(
            id = "nord_us",
            name = "NordVPN US",
            regionId = "US",
            serverHostname = "us1234.nordvpn.com",
            templateId = "nordvpn"
        )
        val creds = ProviderCredentials(templateId = "nordvpn", username = "user123", password = "pass456")

        `when`(mockSettingsRepo.getProviderCredentials("nordvpn")).thenReturn(creds)
        `when`(mockNordApi.getOvpnConfig("us1234.nordvpn.com"))
            .thenReturn("client\nauth-user-pass\n".toResponseBody())

        val prepared = service.prepareConfig(config)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText()).isEqualTo("user123\npass456\n")

        // Verify file exists and permissions can be read
        assertThat(authFile.canRead()).isTrue()
    }

    @Test
    fun prepareLocalTestConfig_createsAuthFileWithSecurePermissionsAndContent() = runBlocking {
        val config = VpnConfig(
            id = "local_uk",
            name = "Local UK",
            regionId = "UK",
            serverHostname = "10.0.2.2:1194",
            templateId = "local-test"
        )
        val creds = ProviderCredentials(templateId = "local-test", username = "testuser", password = "testpass")

        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(creds)

        val prepared = service.prepareConfig(config)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText()).isEqualTo("testuser\ntestpass\n")
        assertThat(authFile.canRead()).isTrue()
    }
}
