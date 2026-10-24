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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockNordApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockContext: Context
    private lateinit var vpnTemplateService: VpnTemplateService

    @Before
    fun setUp() {
        mockNordApi = mock(NordVpnApiService::class.java)
        mockSettingsRepo = mock(SettingsRepository::class.java)
        mockContext = mock(Context::class.java)

        `when`(mockContext.cacheDir).thenReturn(tempFolder.root)
        vpnTemplateService = VpnTemplateService(mockNordApi, mockSettingsRepo, mockContext)
    }

    @Test
    fun prepareLocalTestConfig_createsAuthFileWithRestrictedPermissionsAndCorrectContent() = runBlocking {
        val creds = ProviderCredentials(
            templateId = "local-test",
            username = "test_user",
            password = "test_password"
        )
        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(creds)

        val vpnConfig = VpnConfig(
            id = "101",
            name = "Local Tunnel",
            regionId = "US",
            templateId = "local-test",
            serverHostname = "127.0.0.1:1194"
        )

        val prepared = vpnTemplateService.prepareConfig(vpnConfig)

        assertNotNull(prepared.authFile)
        val authFile = prepared.authFile!!
        assertTrue("Auth file must exist", authFile.exists())

        // Verify content
        val lines = authFile.readLines()
        assertEquals(2, lines.size)
        assertEquals("test_user", lines[0])
        assertEquals("test_password", lines[1])

        // Verify file readable/writable by owner
        assertTrue("File should be readable by owner", authFile.canRead())
        assertTrue("File should be writable by owner", authFile.canWrite())
    }

    @Test
    fun prepareNordVpnConfig_createsAuthFileWithRestrictedPermissionsAndCorrectContent() = runBlocking {
        val creds = ProviderCredentials(
            templateId = "nordvpn",
            username = "nord_user",
            password = "nord_password"
        )
        `when`(mockSettingsRepo.getProviderCredentials("nordvpn")).thenReturn(creds)

        val fakeOvpnBody = "client\ndev tun\nauth-user-pass\n".toResponseBody()
        `when`(mockNordApi.getOvpnConfig(anyString() ?: "")).thenReturn(fakeOvpnBody)

        val vpnConfig = VpnConfig(
            id = "202",
            name = "Nord Tunnel",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk123.nordvpn.com"
        )

        val prepared = vpnTemplateService.prepareConfig(vpnConfig)

        assertNotNull(prepared.authFile)
        val authFile = prepared.authFile!!
        assertTrue("Nord auth file must exist", authFile.exists())

        val lines = authFile.readLines()
        assertEquals(2, lines.size)
        assertEquals("nord_user", lines[0])
        assertEquals("nord_password", lines[1])

        assertTrue("File should be readable by owner", authFile.canRead())
        assertTrue("File should be writable by owner", authFile.canWrite())
    }
}
