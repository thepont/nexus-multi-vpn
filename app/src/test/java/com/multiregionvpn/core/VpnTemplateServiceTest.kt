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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Unit tests for VpnTemplateService to verify configuration preparation and security hardening.
 */
class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockNordVpnApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockContext: Context
    private lateinit var cacheDir: File

    private lateinit var vpnTemplateService: VpnTemplateService

    @Before
    fun setup() {
        mockNordVpnApi = mockk()
        mockSettingsRepo = mockk()
        mockContext = mockk()

        // Use the JUnit temporary folder as the cache directory
        cacheDir = tempFolder.newFolder("cache")
        every { mockContext.cacheDir } returns cacheDir

        vpnTemplateService = VpnTemplateService(
            mockNordVpnApi,
            mockSettingsRepo,
            mockContext
        )
    }

    @Test
    fun test_prepareLocalTestConfig_createsOwnerOnlyAuthFile() = runBlocking {
        // GIVEN: A mock local test configuration and credentials
        val config = VpnConfig(
            id = "test-config-1",
            name = "Local FR",
            regionId = "FR",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1199"
        )
        val creds = ProviderCredentials(
            templateId = "local-test",
            username = "test-user",
            password = "test-password"
        )
        coEvery { mockSettingsRepo.getProviderCredentials("local-test") } returns creds

        // WHEN: Preparing the configuration
        val prepared = vpnTemplateService.prepareConfig(config)

        // THEN: The auth file must be successfully created and contain credentials
        assertThat(prepared).isNotNull()
        val authFile = prepared.authFile
        assertThat(authFile).isNotNull()
        assertThat(authFile!!.exists()).isTrue()

        val content = authFile.readText(Charsets.UTF_8)
        assertThat(content).isEqualTo("test-user\ntest-password\n")

        // AND: The auth file permissions must be securely restricted to owner-only
        try {
            val permissions = Files.getPosixFilePermissions(authFile.toPath())
            assertThat(permissions).contains(PosixFilePermission.OWNER_READ)
            assertThat(permissions).contains(PosixFilePermission.OWNER_WRITE)
            assertThat(permissions).doesNotContain(PosixFilePermission.GROUP_READ)
            assertThat(permissions).doesNotContain(PosixFilePermission.GROUP_WRITE)
            assertThat(permissions).doesNotContain(PosixFilePermission.OTHERS_READ)
            assertThat(permissions).doesNotContain(PosixFilePermission.OTHERS_WRITE)
        } catch (e: UnsupportedOperationException) {
            // Fallback for non-POSIX file systems (e.g. Windows)
            assertThat(authFile.canRead()).isTrue()
            assertThat(authFile.canWrite()).isTrue()
        }
    }
}
