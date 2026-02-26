package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var service: VpnTemplateService
    private val nordVpnApi = mockk<NordVpnApiService>()
    private val settingsRepo = mockk<SettingsRepository>()
    private val context = mockk<Context>()

    @Before
    fun setup() {
        every { context.cacheDir } returns tempFolder.root
        service = VpnTemplateService(nordVpnApi, settingsRepo, context)
    }

    @Test
    fun `cleanupTemporaryFiles should delete auth files`() {
        // Create some fake auth files
        val nordFile = File(tempFolder.root, "nord_auth_1.txt")
        nordFile.writeText("test")
        val localFile = File(tempFolder.root, "local_test_auth_2.txt")
        localFile.writeText("test")
        val otherFile = File(tempFolder.root, "other_file.txt")
        otherFile.writeText("test")

        assertTrue(nordFile.exists())
        assertTrue(localFile.exists())
        assertTrue(otherFile.exists())

        service.cleanupTemporaryFiles()

        assertFalse(nordFile.exists(), "Nord auth file should be deleted")
        assertFalse(localFile.exists(), "Local test auth file should be deleted")
        assertTrue(otherFile.exists(), "Other files should NOT be deleted")
    }
}
