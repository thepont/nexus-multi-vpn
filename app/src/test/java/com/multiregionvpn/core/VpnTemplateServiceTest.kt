package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import com.google.common.truth.Truth.assertThat
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

        // Mock context.cacheDir to use JUnit's temporary folder
        every { mockContext.cacheDir } returns tempFolder.root

        service = VpnTemplateService(mockNordVpnApi, mockSettingsRepo, mockContext)
    }

    @Test
    fun test_prepareConfig_localTest_createsSecureAuthFile() = runTest {
        // GIVEN: local test credentials exist
        val config = VpnConfig("test-id", "Local Server", "UK", "local-test", "127.0.0.1:1194")
        val creds = ProviderCredentials("local-test", "user123", "pass456")
        coEvery { mockSettingsRepo.getProviderCredentials("local-test") } returns creds

        // WHEN: prepareConfig is called
        val result = service.prepareConfig(config)

        // THEN: The returned config is prepared correctly
        assertThat(result).isNotNull()
        assertThat(result.vpnConfig).isEqualTo(config)
        assertThat(result.authFile).isNotNull()

        val authFile = result.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.name).isEqualTo("local_test_auth_test-id.txt")

        // Verify content
        val lines = authFile.readLines()
        assertThat(lines).hasSize(2)
        assertThat(lines[0]).isEqualTo("user123")
        assertThat(lines[1]).isEqualTo("pass456")
    }
}
