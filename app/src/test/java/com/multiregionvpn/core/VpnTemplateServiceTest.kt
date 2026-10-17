package com.multiregionvpn.core

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import kotlinx.coroutines.runBlocking
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

    private lateinit var nordVpnApi: NordVpnApiService
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var context: Context
    private lateinit var service: VpnTemplateService

    @Before
    fun setUp() {
        nordVpnApi = mock(NordVpnApiService::class.java)
        settingsRepo = mock(SettingsRepository::class.java)
        context = mock(Context::class.java)

        `when`(context.cacheDir).thenReturn(tempFolder.root)

        service = VpnTemplateService(nordVpnApi, settingsRepo, context)
    }

    @Test
    fun prepareLocalTestConfig_createsSecureAuthFileWithCredentials() = runBlocking {
        val config = VpnConfig(
            id = "101",
            name = "Local UK Tunnel",
            regionId = "UK",
            templateId = "local-test",
            serverHostname = "127.0.0.1:1194"
        )
        val creds = ProviderCredentials(
            templateId = "local-test",
            username = "test_user",
            password = "test_password"
        )

        `when`(settingsRepo.getProviderCredentials("local-test")).thenReturn(creds)

        val prepared = service.prepareConfig(config)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText()).isEqualTo("test_user\ntest_password\n")
        assertThat(authFile.canRead()).isTrue()
        assertThat(authFile.canWrite()).isTrue()
    }

    @Test
    fun prepareNordVpnConfig_createsSecureAuthFileAndModifiesOvpn() = runBlocking {
        val config = VpnConfig(
            id = "202",
            name = "Nord FR Tunnel",
            regionId = "FR",
            templateId = "nordvpn",
            serverHostname = "fr123.nordvpn.com"
        )
        val creds = ProviderCredentials(
            templateId = "nordvpn",
            username = "nord_user",
            password = "nord_password"
        )

        val baseOvpn = "client\ndev tun\nauth-user-pass\nverb 3"
        val responseBody = baseOvpn.toResponseBody(null)

        `when`(settingsRepo.getProviderCredentials("nordvpn")).thenReturn(creds)
        `when`(nordVpnApi.getOvpnConfig("fr123.nordvpn.com")).thenReturn(responseBody)

        val prepared = service.prepareConfig(config)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText()).isEqualTo("nord_user\nnord_password\n")
        assertThat(prepared.ovpnFileContent).contains("auth-user-pass ${authFile.absolutePath}")
    }
}
