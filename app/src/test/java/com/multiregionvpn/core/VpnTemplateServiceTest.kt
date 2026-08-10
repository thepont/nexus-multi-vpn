package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val nordVpnApi: NordVpnApiService = mockk()
    private val settingsRepo: SettingsRepository = mockk()
    private val context: Context = mockk()
    private lateinit var service: VpnTemplateService
    private lateinit var cacheDir: File

    @Before
    fun setup() {
        cacheDir = tempFolder.newFolder("cache")
        every { context.cacheDir } returns cacheDir
        service = VpnTemplateService(nordVpnApi, settingsRepo, context)
    }

    @Test
    fun `test prepareNordVpnConfig sets owner-only permissions on credentials file`() = runTest {
        // GIVEN
        val config = VpnConfig(
            id = "test_config_id",
            name = "NordVPN Test",
            regionId = "US",
            templateId = "nordvpn",
            serverHostname = "us-test.nordvpn.com"
        )
        val responseBody = "client\nauth-user-pass".toResponseBody()
        coEvery { nordVpnApi.getOvpnConfig("us-test.nordvpn.com") } returns responseBody
        coEvery { settingsRepo.getProviderCredentials("nordvpn") } returns ProviderCredentials(
            templateId = "nordvpn",
            username = "test_user",
            password = "test_password"
        )

        // WHEN
        val preparedConfig = service.prepareConfig(config)

        // THEN
        val authFile = preparedConfig.authFile
        assertTrue(authFile != null && authFile.exists(), "Auth file should exist")

        // Check content is correct
        assertEquals("test_user\ntest_password\n", authFile.readText())

        // Check owner-only read/write permissions
        assertTrue(authFile.canRead(), "Owner must be able to read the auth file")
        assertTrue(authFile.canWrite(), "Owner must be able to write to the auth file")
        assertFalse(authFile.canExecute(), "Auth file must not be executable")
    }

    @Test
    fun `test prepareLocalTestConfig sets owner-only permissions on credentials file`() = runTest {
        // GIVEN
        val config = VpnConfig(
            id = "test_local_id",
            name = "Local Test",
            regionId = "US",
            templateId = "local-test",
            serverHostname = "127.0.0.1:1194"
        )
        coEvery { settingsRepo.getProviderCredentials("local-test") } returns ProviderCredentials(
            templateId = "local-test",
            username = "local_user",
            password = "local_password"
        )

        // WHEN
        val preparedConfig = service.prepareConfig(config)

        // THEN
        val authFile = preparedConfig.authFile
        assertTrue(authFile != null && authFile.exists(), "Local test auth file should exist")
        assertEquals("local_user\nlocal_password\n", authFile.readText())

        assertTrue(authFile.canRead(), "Owner must be able to read the local test auth file")
        assertTrue(authFile.canWrite(), "Owner must be able to write to the local test auth file")
        assertFalse(authFile.canExecute(), "Local test auth file must not be executable")
    }
}
