package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    private lateinit var mockNordApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var service: VpnTemplateService

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockNordApi = mock(NordVpnApiService::class.java)
        mockSettingsRepo = mock(SettingsRepository::class.java)

        `when`(mockContext.cacheDir).thenReturn(tempFolder.root)
        service = VpnTemplateService(mockNordApi, mockSettingsRepo, mockContext)
    }

    @Test
    fun prepareConfig_localTest_createsSecureAuthFileWithCorrectPermissionsAndContent() = runTest {
        val config = VpnConfig(
            id = "test_1",
            name = "Test Local",
            regionId = "US",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1194"
        )
        val creds = ProviderCredentials("local-test", "testuser", "testpass")
        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(creds)

        val prepared = service.prepareConfig(config)

        assertNotNull(prepared.authFile)
        val authFile = prepared.authFile!!
        assertTrue(authFile.exists())
        assertEquals("testuser\ntestpass\n", authFile.readText())
        assertTrue("Auth file must be readable by owner", authFile.canRead())
        assertTrue("Auth file must be writable by owner", authFile.canWrite())
    }

    @Test
    fun prepareConfig_nordVpn_createsSecureAuthFileWithCorrectPermissionsAndContent() = runTest {
        val config = VpnConfig(
            id = "nord_1",
            name = "Nord UK",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk1234.nordvpn.com"
        )
        val creds = ProviderCredentials("nordvpn", "norduser", "nordpass")
        `when`(mockSettingsRepo.getProviderCredentials("nordvpn")).thenReturn(creds)

        val mockResponseBody = ResponseBody.create(null, "client\ndev tun\nauth-user-pass\n")
        `when`(mockNordApi.getOvpnConfig(anyString())).thenReturn(mockResponseBody)

        val prepared = service.prepareConfig(config)

        assertNotNull(prepared.authFile)
        val authFile = prepared.authFile!!
        assertTrue(authFile.exists())
        assertEquals("norduser\nnordpass\n", authFile.readText())
        assertTrue("Auth file must be readable by owner", authFile.canRead())
        assertTrue("Auth file must be writable by owner", authFile.canWrite())
        assertTrue(prepared.ovpnFileContent.contains("auth-user-pass ${authFile.absolutePath}"))
    }
}
