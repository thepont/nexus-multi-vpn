package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
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

    private lateinit var nordVpnApi: NordVpnApiService
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var context: Context
    private lateinit var service: VpnTemplateService

    private lateinit var cacheDir: File

    @Before
    fun setup() {
        nordVpnApi = mockk()
        settingsRepo = mockk()
        context = mockk()
        cacheDir = tempFolder.newFolder("cache")
        every { context.cacheDir } returns cacheDir

        service = VpnTemplateService(nordVpnApi, settingsRepo, context)
    }

    @Test
    fun `test prepareLocalTestConfig creates auth file with secure owner-only permissions and correct content`() = runTest {
        // GIVEN: local test credentials
        val creds = ProviderCredentials("local-test", "test-user", "test-pass")
        coEvery { settingsRepo.getProviderCredentials("local-test") } returns creds

        val config = VpnConfig("test-config", "Test Connection", "UK", "local-test", "127.0.0.1:1194")

        // WHEN: preparing the config
        val prepared = service.prepareConfig(config)

        // THEN: verify output config has auth file
        val authFile = prepared.authFile
        assertThat(authFile).isNotNull()
        assertThat(authFile!!.exists()).isTrue()

        // Verify correct credentials written
        val content = authFile.readText(Charsets.UTF_8)
        assertThat(content).isEqualTo("test-user\ntest-pass\n")

        // Verify file was written to context's cacheDir
        assertThat(authFile.parentFile!!.absolutePath).isEqualTo(cacheDir.absolutePath)

        // Clean up
        authFile.delete()
    }
}
