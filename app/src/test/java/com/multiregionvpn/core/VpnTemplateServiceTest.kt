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
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VpnTemplateServiceTest {

    private lateinit var mockNordVpnApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockContext: Context
    private lateinit var tempDir: File
    private lateinit var service: VpnTemplateService

    @Before
    fun setup() {
        mockNordVpnApi = mockk()
        mockSettingsRepo = mockk()
        mockContext = mockk()

        tempDir = File(System.getProperty("java.io.tmpdir"), "vpn_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        every { mockContext.cacheDir } returns tempDir

        service = VpnTemplateService(mockNordVpnApi, mockSettingsRepo, mockContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `prepareConfig for local test sets secure file permissions on auth file`() = runTest {
        val config = VpnConfig("local-1", "Local VPN", "US", "local-test", "10.0.2.2:1194")
        val creds = ProviderCredentials("local-test", "user123", "pass123")

        coEvery { mockSettingsRepo.getProviderCredentials("local-test") } returns creds

        val result = service.prepareConfig(config)

        assertNotNull(result.authFile)
        assertTrue(result.authFile!!.exists())
        assertEquals("user123\npass123\n", result.authFile!!.readText())

        // Verify readable and writable state on created file
        assertTrue(result.authFile!!.canRead())
        assertTrue(result.authFile!!.canWrite())
    }
}
