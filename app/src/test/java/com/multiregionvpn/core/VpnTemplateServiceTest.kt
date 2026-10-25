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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var nordVpnApi: NordVpnApiService
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var context: Context
    private lateinit var service: VpnTemplateService

    @Before
    fun setUp() {
        nordVpnApi = mock(NordVpnApiService::class.java)
        settingsRepo = mock(SettingsRepository::class.java)
        context = mock(Context::class.java)
        `when`(context.cacheDir).thenReturn(tempFolder.root)
        service = VpnTemplateService(nordVpnApi, settingsRepo, context)
    }

    @Test
    fun testPrepareLocalTestConfig_createsAuthFileWithRestrictedPermissions() = runBlocking {
        val config = VpnConfig(
            id = "test-config-1",
            name = "Test Tunnel",
            regionId = "US",
            templateId = "local-test",
            serverHostname = "127.0.0.1:1194"
        )
        val creds = ProviderCredentials(
            templateId = "local-test",
            username = "testuser",
            password = "testpassword"
        )

        `when`(settingsRepo.getProviderCredentials(anyString() ?: "")).thenReturn(creds)

        val prepared = service.prepareConfig(config)
        val authFile = prepared.authFile

        assertNotNull(authFile)
        assertTrue(authFile!!.exists())
        assertEquals("testuser\ntestpassword\n", authFile.readText(Charsets.UTF_8))

        // Verify owner permissions are retained and accessible
        assertTrue(authFile.canRead())
        assertTrue(authFile.canWrite())
    }
}
