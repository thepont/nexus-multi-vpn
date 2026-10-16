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
    private lateinit var cacheDir: File

    @Before
    fun setup() {
        cacheDir = tempFolder.newFolder("cache")
        mockContext = mock(Context::class.java)
        mockNordVpnApi = mock(NordVpnApiService::class.java)
        mockSettingsRepo = mock(SettingsRepository::class.java)

        `when`(mockContext.cacheDir).thenReturn(cacheDir)

        vpnTemplateService = VpnTemplateService(
            nordVpnApi = mockNordVpnApi,
            settingsRepo = mockSettingsRepo,
            context = mockContext
        )
    }

    @Test
    fun prepareLocalTestConfig_createsAuthFileWithCorrectPermissionsAndContent() = runBlocking {
        val testConfig = VpnConfig(
            id = "test_config_1",
            name = "Test Server",
            regionId = "UK",
            templateId = "local-test",
            serverHostname = "127.0.0.1:1194"
        )

        val testCreds = ProviderCredentials(
            templateId = "local-test",
            username = "testuser",
            password = "testpassword"
        )

        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(testCreds)

        val preparedConfig = vpnTemplateService.prepareConfig(testConfig)

        assertNotNull(preparedConfig.authFile)
        val authFile = preparedConfig.authFile!!

        assertTrue("Auth file should exist", authFile.exists())
        assertEquals("testuser\ntestpassword\n", authFile.readText(Charsets.UTF_8))

        // On POSIX systems, check owner-only readable/writable
        assertTrue("Auth file should be readable", authFile.canRead())
        assertTrue("Auth file should be writable", authFile.canWrite())
    }

    @Test
    fun prepareNordVpnConfig_createsAuthFileWithCorrectPermissionsAndContent() = runBlocking {
        val testConfig = VpnConfig(
            id = "nord_config_1",
            name = "Nord UK",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk1234.nordvpn.com"
        )

        val testCreds = ProviderCredentials(
            templateId = "nordvpn",
            username = "norduser",
            password = "nordpassword"
        )

        `when`(mockSettingsRepo.getProviderCredentials("nordvpn")).thenReturn(testCreds)
        `when`(mockNordVpnApi.getOvpnConfig("uk1234.nordvpn.com"))
            .thenReturn("client\ndev tun\nauth-user-pass\n".toResponseBody())

        val preparedConfig = vpnTemplateService.prepareConfig(testConfig)

        assertNotNull(preparedConfig.authFile)
        val authFile = preparedConfig.authFile!!

        assertTrue("Auth file should exist", authFile.exists())
        assertEquals("norduser\nnordpassword\n", authFile.readText(Charsets.UTF_8))
        assertTrue("Config should replace auth-user-pass with auth file path",
            preparedConfig.ovpnFileContent.contains("auth-user-pass ${authFile.absolutePath}"))
    }
}
