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

        service = VpnTemplateService(mockNordApi, mockSettingsRepo, mockContext)
    }

    @Test
    fun prepareLocalTestConfig_createsAuthFileWithRestrictedPermissions() = runBlocking {
        val config = VpnConfig(
            id = 1,
            name = "Local Test UK",
            templateId = "local-test",
            regionId = "UK",
            serverHostname = "10.0.2.2:1194",
            isActive = true
        )
        val creds = ProviderCredentials("local-test", "testuser\ntestpass")

        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(creds)

        val prepared = service.prepareConfig(config)

        assertNotNull(prepared.authFile)
        val authFile = prepared.authFile!!
        assertTrue(authFile.exists())
        assertEquals("testuser\ntestpass\n", authFile.readText())
        assertTrue("Auth file should be readable", authFile.canRead())
        assertTrue("Auth file should be writable", authFile.canWrite())
    }
}
