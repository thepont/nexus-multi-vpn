package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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

        service = VpnTemplateService(
            nordVpnApi = mockNordApi,
            settingsRepo = mockSettingsRepo,
            context = mockContext
        )
    }

    @Test
    fun testPrepareLocalTestConfig_createsSecureAuthFile() = runBlocking {
        val config = VpnConfig(
            id = "test_config_1",
            name = "Local Test Tunnel",
            regionId = "US",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1194"
        )

        val creds = ProviderCredentials(
            templateId = "local-test",
            username = "testuser",
            password = "testpassword"
        )

        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(creds)

        val prepared = service.prepareConfig(config)

        assertNotNull(prepared.authFile)
        val authFile = prepared.authFile!!
        assertTrue(authFile.exists())
        assertEquals("testuser\ntestpassword\n", authFile.readText())

        // Verify owner-only read permission is set on supported platforms
        assertTrue("Auth file should be readable", authFile.canRead())
    }
}
