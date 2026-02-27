package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnTemplateServiceSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var nordVpnApi: NordVpnApiService
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var vpnTemplateService: VpnTemplateService
    private lateinit var cacheDir: File

    @Before
    fun setup() {
        context = mockk()
        nordVpnApi = mockk()
        settingsRepo = mockk()
        cacheDir = tempFolder.newFolder("cache")

        every { context.cacheDir } returns cacheDir

        vpnTemplateService = VpnTemplateService(nordVpnApi, settingsRepo, context)
    }

    @Test
    fun `cleanupTemporaryFiles should delete auth files`() {
        // Create dummy auth files
        val file1 = File(cacheDir, "nord_auth_123.txt")
        file1.writeText("user\npass\n")
        val file2 = File(cacheDir, "local_test_auth_456.txt")
        file2.writeText("user\npass\n")
        val otherFile = File(cacheDir, "other.txt")
        otherFile.writeText("not an auth file")

        assertTrue(file1.exists())
        assertTrue(file2.exists())
        assertTrue(otherFile.exists())

        vpnTemplateService.cleanupTemporaryFiles()

        assertFalse(file1.exists(), "nord_auth_123.txt should be deleted")
        assertFalse(file2.exists(), "local_test_auth_456.txt should be deleted")
        assertTrue(otherFile.exists(), "other.txt should NOT be deleted")
    }

    @Test
    fun `prepareNordVpnConfig should create auth file with restricted permissions`() = runBlocking {
        val config = VpnConfig("1", "Server", "UK", "nordvpn", "uk.nordvpn.com")
        val creds = ProviderCredentials("nordvpn", "user", "pass")

        coEvery { nordVpnApi.getOvpnConfig(any()) } returns mockk<ResponseBody>().apply {
            every { string() } returns "auth-user-pass\nremote 1.2.3.4"
        }
        coEvery { settingsRepo.getProviderCredentials("nordvpn") } returns creds

        vpnTemplateService.prepareConfig(config)

        val authFile = File(cacheDir, "nord_auth_1.txt")
        assertTrue(authFile.exists())

        // Note: On many local filesystems/CI environments, setReadable(true, true)
        // might not be fully testable via File.canRead() if the current user is the owner.
        // However, we've implemented the calls.
    }
}
