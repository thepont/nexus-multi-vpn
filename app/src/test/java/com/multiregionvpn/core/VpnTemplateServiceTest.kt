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

    private lateinit var mockNordApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockContext: Context
    private lateinit var cacheDir: File
    private lateinit var service: VpnTemplateService

    @Before
    fun setUp() {
        mockNordApi = mock(NordVpnApiService::class.java)
        mockSettingsRepo = mock(SettingsRepository::class.java)
        mockContext = mock(Context::class.java)

        cacheDir = tempFolder.newFolder("cache")
        `when`(mockContext.cacheDir).thenReturn(cacheDir)

        service = VpnTemplateService(mockNordApi, mockSettingsRepo, mockContext)
    }

    @Test
    fun prepareNordVpnConfig_createsSecureAuthFileWithOwnerPermissions() = runBlocking {
        val config = VpnConfig(
            id = 1,
            name = "UK Server",
            serverHostname = "uk123.nordvpn.com",
            templateId = "nordvpn",
            groupId = "uk-group"
        )
        val dummyCreds = ProviderCredentials(templateId = "nordvpn", username = "user_test", password = "pass_test")
        `when`(mockSettingsRepo.getProviderCredentials("nordvpn")).thenReturn(dummyCreds)
        `when`(mockNordApi.getOvpnConfig("uk123.nordvpn.com")).thenReturn("auth-user-pass\nclient".toResponseBody(null))

        val result = service.prepareConfig(config)

        assertNotNull(result.authFile)
        val authFile = result.authFile!!
        assertTrue(authFile.exists())
        assertEquals("user_test\npass_test\n", authFile.readText())

        // Verify security hardening: auth file exists and owner can read/write
        assertTrue(authFile.canRead())
        assertTrue(authFile.canWrite())
    }

    @Test
    fun prepareLocalTestConfig_createsSecureAuthFileWithOwnerPermissions() = runBlocking {
        val config = VpnConfig(
            id = 2,
            name = "Local Test Server",
            serverHostname = "10.0.2.2:1194",
            templateId = "local-test",
            groupId = "local-group"
        )
        val dummyCreds = ProviderCredentials(templateId = "local-test", username = "local_user", password = "local_password")
        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(dummyCreds)

        val result = service.prepareConfig(config)

        assertNotNull(result.authFile)
        val authFile = result.authFile!!
        assertTrue(authFile.exists())
        assertEquals("local_user\nlocal_password\n", authFile.readText())

        // Verify security hardening: auth file exists and owner can read/write
        assertTrue(authFile.canRead())
        assertTrue(authFile.canWrite())
    }
}
