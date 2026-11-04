package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit test for VpnTemplateService security permissions on sensitive auth credential files.
 */
class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun test_secureAuthFilePermissions_restrictsAccess() {
        val testFile = tempFolder.newFile("test_auth.txt")
        testFile.writeText("test_user\ntest_pass\n", Charsets.UTF_8)

        VpnTemplateService.secureAuthFilePermissions(testFile)

        assertThat(testFile.canRead()).isTrue()
        assertThat(testFile.canWrite()).isTrue()
        assertThat(testFile.canExecute()).isFalse()
        assertThat(testFile.readText(Charsets.UTF_8)).isEqualTo("test_user\ntest_pass\n")
    }
}
