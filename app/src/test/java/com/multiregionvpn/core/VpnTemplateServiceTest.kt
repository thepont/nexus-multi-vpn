package com.multiregionvpn.core

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val nordVpnApi: NordVpnApiService = mock(NordVpnApiService::class.java)
    private val settingsRepo: SettingsRepository = mock(SettingsRepository::class.java)
    private val context: Context = mock(Context::class.java)

    private lateinit var vpnTemplateService: VpnTemplateService

    @Before
    fun setUp() {
        `when`(context.cacheDir).thenReturn(tempFolder.root)
        vpnTemplateService = VpnTemplateService(nordVpnApi, settingsRepo, context)
    }

    @Test
    fun testPrepareNordVpnConfig_createsAuthFileWithSecurePermissionsAndContent() = runTest {
        val config = VpnConfig(
            id = "nord_uk",
            name = "NordVPN UK",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk123.nordvpn.com"
        )
        val creds = ProviderCredentials(
            templateId = "nordvpn",
            username = "user_nord",
            password = "pass_nord"
        )

        `when`(nordVpnApi.getOvpnConfig("uk123.nordvpn.com"))
            .thenReturn("client\nauth-user-pass\nproto udp".toResponseBody())
        `when`(settingsRepo.getProviderCredentials("nordvpn"))
            .thenReturn(creds)

        val prepared = vpnTemplateService.prepareConfig(config)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText(Charsets.UTF_8)).isEqualTo("user_nord\npass_nord\n")
        assertThat(authFile.canRead()).isTrue()
        assertThat(authFile.canWrite()).isTrue()
        assertThat(prepared.ovpnFileContent).contains("auth-user-pass ${authFile.absolutePath}")
    }

    @Test
    fun testPrepareLocalTestConfig_createsAuthFileWithSecurePermissionsAndContent() = runTest {
        val config = VpnConfig(
            id = "local_uk",
            name = "Local Test UK",
            regionId = "UK",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1194"
        )
        val creds = ProviderCredentials(
            templateId = "local-test",
            username = "local_user",
            password = "local_password"
        )

        `when`(settingsRepo.getProviderCredentials("local-test"))
            .thenReturn(creds)

        val prepared = vpnTemplateService.prepareConfig(config)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText(Charsets.UTF_8)).isEqualTo("local_user\nlocal_password\n")
        assertThat(authFile.canRead()).isTrue()
        assertThat(authFile.canWrite()).isTrue()
        assertThat(prepared.ovpnFileContent).contains("auth-user-pass ${authFile.absolutePath}")
    }
}
