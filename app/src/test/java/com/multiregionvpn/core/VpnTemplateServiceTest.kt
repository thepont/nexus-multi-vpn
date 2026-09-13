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
import okhttp3.ResponseBody
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
    fun `prepareConfig for local-test creates secure auth file with owner permissions`() = runTest {
        val config = VpnConfig("test-id-1", "Local Test", "US", "local-test", "10.0.2.2:1194")
        val creds = ProviderCredentials("local-test", "testuser", "testpass")
        coEvery { settingsRepo.getProviderCredentials("local-test") } returns creds

        val result = service.prepareConfig(config)

        assertThat(result.authFile).isNotNull()
        val authFile = result.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText()).isEqualTo("testuser\ntestpass\n")

        // Verify file created in cacheDir
        assertThat(authFile.parentFile?.absolutePath).isEqualTo(cacheDir.absolutePath)
    }

    @Test
    fun `prepareConfig for nordvpn creates secure auth file and updates config string`() = runTest {
        val config = VpnConfig("nord-id-1", "Nord UK", "UK", "nordvpn", "uk123.nordvpn.com")
        val creds = ProviderCredentials("nordvpn", "norduser", "nordpass")
        val responseBody = mockk<ResponseBody>()
        every { responseBody.string() } returns "client\ndev tun\nauth-user-pass\n"

        coEvery { settingsRepo.getProviderCredentials("nordvpn") } returns creds
        coEvery { nordVpnApi.getOvpnConfig("uk123.nordvpn.com") } returns responseBody

        val result = service.prepareConfig(config)

        assertThat(result.authFile).isNotNull()
        val authFile = result.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText()).isEqualTo("norduser\nnordpass\n")
        assertThat(result.ovpnFileContent).contains("auth-user-pass ${authFile.absolutePath}")
    }
}
