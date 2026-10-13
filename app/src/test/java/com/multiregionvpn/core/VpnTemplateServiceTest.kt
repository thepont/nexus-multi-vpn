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
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockNordVpnApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var service: VpnTemplateService

    @Before
    fun setup() {
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
    fun testPrepareNordVpnConfig_createsSecureAuthFile() = runBlocking {
        val fakeConfig = VpnConfig(
            id = "test_nord_1",
            name = "Test Nord",
            regionId = "US",
            templateId = "nordvpn",
            serverHostname = "us123.nordvpn.com"
        )
        val fakeCreds = ProviderCredentials(
            templateId = "nordvpn",
            username = "test_user",
            password = "test_password"
        )

        `when`(mockSettingsRepo.getProviderCredentials("nordvpn")).thenReturn(fakeCreds)
        `when`(mockNordVpnApi.getOvpnConfig(anyString())).thenReturn("client\nauth-user-pass".toResponseBody(null))

        val result = service.prepareConfig(fakeConfig)

        val authFile = result.authFile
        assertNotNull(authFile)
        assertTrue(authFile!!.exists())
        assertEquals("test_user\ntest_password\n", authFile.readText(Charsets.UTF_8))
    }

    @Test
    fun testPrepareLocalTestConfig_createsSecureAuthFile() = runBlocking {
        val fakeConfig = VpnConfig(
            id = "test_local_1",
            name = "Test Local",
            regionId = "LOCAL",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1194"
        )
        val fakeCreds = ProviderCredentials(
            templateId = "local-test",
            username = "local_user",
            password = "local_password"
        )

        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(fakeCreds)

        val result = service.prepareConfig(fakeConfig)

        val authFile = result.authFile
        assertNotNull(authFile)
        assertTrue(authFile!!.exists())
        assertEquals("local_user\nlocal_password\n", authFile.readText(Charsets.UTF_8))
    }
}
