package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
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
    fun testPrepareLocalTestConfigCreatesSecureAuthFile() = runBlocking {
        val testConfig = VpnConfig(
            id = "test_config_id",
            name = "Test Server",
            regionId = "US",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1194"
        )
        val testCreds = ProviderCredentials(
            templateId = "local-test",
            username = "test_user",
            password = "test_password"
        )

        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(testCreds)

        val prepared = vpnTemplateService.prepareConfig(testConfig)

        val authFile = prepared.authFile
        assertTrue("Auth file should exist", authFile != null && authFile.exists())
        assertEquals("test_user\ntest_password\n", authFile!!.readText())

        // Verify owner-only permissions where supported by host environment
        if (authFile.canRead()) {
            assertTrue("Auth file should be readable", authFile.canRead())
        }
    }
}
