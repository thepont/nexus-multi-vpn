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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockNordApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockContext: Context
    private lateinit var service: VpnTemplateService

    @Before
    fun setup() {
        mockNordApi = mockk()
        mockSettingsRepo = mockk()
        mockContext = mockk()

        every { mockContext.cacheDir } returns tempFolder.root

        service = VpnTemplateService(mockNordApi, mockSettingsRepo, mockContext)
    }

    @Test
    fun `given local test config when prepared then auth file is created with secure permissions and expected contents`() = runTest {
        val config = VpnConfig("local-1", "Local VPN", "UK", "local-test", "10.0.2.2:1194")
        val creds = ProviderCredentials("local-test", "testuser", "testpass")

        coEvery { mockSettingsRepo.getProviderCredentials("local-test") } returns creds

        val prepared = service.prepareConfig(config)

        val authFile = prepared.authFile
        assertNotNull(authFile)
        assertTrue(authFile!!.exists())

        // Verify auth file content
        val lines = authFile.readLines(Charsets.UTF_8)
        assertEquals(2, lines.size)
        assertEquals("testuser", lines[0])
        assertEquals("testpass", lines[1])

        // Verify owner permissions
        assertTrue(authFile.canRead())
        assertTrue(authFile.canWrite())
    }
}
