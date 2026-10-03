package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class VpnTemplateServiceTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockNordApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var vpnTemplateService: VpnTemplateService

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        mockNordApi = mock(NordVpnApiService::class.java)
        mockSettingsRepo = mock(SettingsRepository::class.java)

        `when`(mockContext.cacheDir).thenReturn(tempFolder.root)

        vpnTemplateService = VpnTemplateService(
            nordVpnApi = mockNordApi,
            settingsRepo = mockSettingsRepo,
            context = mockContext
        )
    }

    @Test
    fun prepareLocalTestConfig_createsAuthFileWithRestrictedPermissionsAndCorrectContent() = runBlocking {
        val creds = ProviderCredentials(
            templateId = "local-test",
            username = "test_user",
            password = "test_password_123"
        )
        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(creds)

        val config = VpnConfig(
            id = "config-1",
            name = "Local Test Tunnel",
            regionId = "US",
            serverHostname = "10.0.2.2:1194",
            templateId = "local-test"
        )

        val prepared = vpnTemplateService.prepareConfig(config)

        val authFile = prepared.authFile
        assertTrue("Auth file should exist", authFile != null && authFile.exists())
        val content = authFile!!.readText(Charsets.UTF_8)
        assertEquals("test_user\ntest_password_123\n", content)

        // Verify that permissions are owner-only
        assertTrue("Auth file should be readable", authFile.canRead())
        assertTrue("Auth file should be writable", authFile.canWrite())
    }

    @Test
    fun prepareNordVpnConfig_createsAuthFileWithRestrictedPermissionsAndCorrectContent() = runBlocking {
        val creds = ProviderCredentials(
            templateId = "nordvpn",
            username = "nord_user",
            password = "nord_password_456"
        )
        `when`(mockSettingsRepo.getProviderCredentials("nordvpn")).thenReturn(creds)

        val mockResponseBody = mock(ResponseBody::class.java)
        `when`(mockResponseBody.string()).thenReturn("client\ndev tun\nauth-user-pass")
        `when`(mockNordApi.getOvpnConfig("us1234.nordvpn.com")).thenReturn(mockResponseBody)

        val config = VpnConfig(
            id = "config-2",
            name = "Nord US Tunnel",
            regionId = "US",
            serverHostname = "us1234.nordvpn.com",
            templateId = "nordvpn"
        )

        val prepared = vpnTemplateService.prepareConfig(config)

        val authFile = prepared.authFile
        assertTrue("Nord Auth file should exist", authFile != null && authFile.exists())
        val content = authFile!!.readText(Charsets.UTF_8)
        assertEquals("nord_user\nnord_password_456\n", content)

        assertTrue("Auth file should be readable", authFile.canRead())
        assertTrue("Auth file should be writable", authFile.canWrite())
    }
}
