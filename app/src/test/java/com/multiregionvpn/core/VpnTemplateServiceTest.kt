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
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VpnTemplateServiceTest {

    private lateinit var nordVpnApi: NordVpnApiService
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var context: Context
    private lateinit var tempCacheDir: File
    private lateinit var service: VpnTemplateService

    @Before
    fun setup() {
        nordVpnApi = mockk()
        settingsRepo = mockk()
        context = mockk()
        tempCacheDir = Files.createTempDirectory("vpn_template_test_cache").toFile()
        every { context.cacheDir } returns tempCacheDir

        service = VpnTemplateService(nordVpnApi, settingsRepo, context)
    }

    @After
    fun tearDown() {
        tempCacheDir.deleteRecursively()
    }

    @Test
    fun testPrepareConfigCreatesSecureAuthFile() = runTest {
        // GIVEN: local-test VPN configuration and valid credentials
        val config = VpnConfig("local_uk", "Local UK", "UK", "local-test", "10.0.2.2:1194")
        val creds = ProviderCredentials("local-test", "testuser", "testpass")
        coEvery { settingsRepo.getProviderCredentials("local-test") } returns creds

        // WHEN: prepareConfig is called
        val preparedConfig = service.prepareConfig(config)

        // THEN: authFile is created securely with owner-only access
        val authFile = preparedConfig.authFile
        assertThat(authFile).isNotNull()
        assertThat(authFile!!.exists()).isTrue()
        assertThat(authFile.canRead()).isTrue()
        assertThat(authFile.canWrite()).isTrue()

        // Verify file contents match username and password
        val content = authFile.readText()
        assertThat(content).isEqualTo("testuser\ntestpass\n")
    }
}
