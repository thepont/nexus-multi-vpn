package com.multiregionvpn.core

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
    private lateinit var service: VpnTemplateService

    @Before
    fun setup() {
        mockNordVpnApi = mockk()
        mockSettingsRepo = mockk()
        mockContext = mockk()

        // Return our temporary folder as cache directory
        every { mockContext.cacheDir } returns tempFolder.newFolder()

        service = VpnTemplateService(mockNordVpnApi, mockSettingsRepo, mockContext)
    }

    @Test
    fun `prepareLocalTestConfig creates auth file with strict permissions`() = runTest {
        // GIVEN: A configuration and valid credentials
        val config = VpnConfig("config-123", "Test Server", "UK", "local-test", "10.0.2.2:1194")
        val creds = ProviderCredentials("local-test", "user123", "pass456")
        coEvery { mockSettingsRepo.getProviderCredentials("local-test") } returns creds

        // WHEN: Preparing the local test config
        val result = service.prepareConfig(config)

        // THEN: The auth file should be created securely
        val authFile = result.authFile
        assertThat(authFile).isNotNull()
        assertThat(authFile!!.exists()).isTrue()

        // Verify contents are written correctly
        val content = authFile.readText()
        assertThat(content).isEqualTo("user123\npass456\n")

        // Clean up the file as NativeOpenVpnClient would normally delete it
        authFile.delete()
    }
}
