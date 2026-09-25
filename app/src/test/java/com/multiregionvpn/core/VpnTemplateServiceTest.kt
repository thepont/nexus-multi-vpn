package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockNordVpnApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var vpnTemplateService: VpnTemplateService

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockNordVpnApi = mock(NordVpnApiService::class.java)
        mockSettingsRepo = mock(SettingsRepository::class.java)

        `when`(mockContext.cacheDir).thenReturn(tempFolder.root)

        vpnTemplateService = VpnTemplateService(
            nordVpnApi = mockNordVpnApi,
            settingsRepo = mockSettingsRepo,
            context = mockContext
        )
    }

    @Test
    fun prepareNordVpnConfig_createsSecureAuthFileWithCorrectContent() = runBlocking {
        val config = VpnConfig(
            id = "test-nord-1",
            name = "Nord Test",
            regionId = "US",
            templateId = "nordvpn",
            serverHostname = "us123.nordvpn.com"
        )
        val creds = ProviderCredentials(
            templateId = "nordvpn",
            username = "testuser",
            password = "testpassword"
        )

        `when`(mockSettingsRepo.getProviderCredentials("nordvpn")).thenReturn(creds)
        `when`(mockNordVpnApi.getOvpnConfig("us123.nordvpn.com"))
            .thenReturn("client\ndev tun\nauth-user-pass\n".toResponseBody())

        val prepared = vpnTemplateService.prepareConfig(config)

        assertNotNull(prepared.authFile)
        val authFile = prepared.authFile!!
        assertTrue(authFile.exists())
        assertEquals("testuser\ntestpassword\n", authFile.readText())
    }

    @Test
    fun prepareLocalTestConfig_createsSecureAuthFileWithCorrectContent() = runBlocking {
        val config = VpnConfig(
            id = "test-local-1",
            name = "Local Test",
            regionId = "LOCAL",
            templateId = "local-test",
            serverHostname = "127.0.0.1:1194"
        )
        val creds = ProviderCredentials(
            templateId = "local-test",
            username = "localuser",
            password = "localpassword"
        )

        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(creds)

        val prepared = vpnTemplateService.prepareConfig(config)

        assertNotNull(prepared.authFile)
        val authFile = prepared.authFile!!
        assertTrue(authFile.exists())
        assertEquals("localuser\nlocalpassword\n", authFile.readText())
    }
}
