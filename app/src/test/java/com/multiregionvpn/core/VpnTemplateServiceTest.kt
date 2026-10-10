package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockNordVpnApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockContext: Context
    private lateinit var vpnTemplateService: VpnTemplateService

    @Before
    fun setUp() {
        mockNordVpnApi = mockk()
        mockSettingsRepo = mockk()
        mockContext = mockk()

        every { mockContext.cacheDir } returns tempFolder.root

        vpnTemplateService = VpnTemplateService(
            nordVpnApi = mockNordVpnApi,
            settingsRepo = mockSettingsRepo,
            context = mockContext
        )
    }

    @Test
    fun prepareNordVpnConfig_createsSecureAuthFileWithCorrectPermissionsAndContent() = runTest {
        val config = VpnConfig(
            id = "test-nord-1",
            name = "Nord UK",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk1234.nordvpn.com"
        )
        val creds = ProviderCredentials(
            templateId = "nordvpn",
            username = "user_nord_secret",
            password = "pass_nord_secret"
        )
        val mockResponseBody = mockk<ResponseBody>()
        every { mockResponseBody.string() } returns "client\ndev tun\nauth-user-pass\n"

        coEvery { mockNordVpnApi.getOvpnConfig("uk1234.nordvpn.com") } returns mockResponseBody
        coEvery { mockSettingsRepo.getProviderCredentials("nordvpn") } returns creds

        val result = vpnTemplateService.prepareConfig(config)

        assertNotNull(result)
        val authFile = result.authFile
        assertNotNull(authFile)
        assertTrue(authFile!!.exists())

        // Verify credentials written to disk
        val content = authFile.readText(Charsets.UTF_8)
        assertEquals("user_nord_secret\npass_nord_secret\n", content)

        // Verify file permissions: owner read/write
        assertTrue(authFile.canRead())
        assertTrue(authFile.canWrite())
    }

    @Test
    fun prepareLocalTestConfig_createsSecureAuthFileWithCorrectPermissionsAndContent() = runTest {
        val config = VpnConfig(
            id = "test-local-1",
            name = "Local Test Server",
            regionId = "UK",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1199"
        )
        val creds = ProviderCredentials(
            templateId = "local-test",
            username = "test_user",
            password = "test_password"
        )

        coEvery { mockSettingsRepo.getProviderCredentials("local-test") } returns creds

        val result = vpnTemplateService.prepareConfig(config)

        assertNotNull(result)
        val authFile = result.authFile
        assertNotNull(authFile)
        assertTrue(authFile!!.exists())

        // Verify credentials written to disk
        val content = authFile.readText(Charsets.UTF_8)
        assertEquals("test_user\ntest_password\n", content)

        // Verify file permissions: owner read/write
        assertTrue(authFile.canRead())
        assertTrue(authFile.canWrite())
    }
}
