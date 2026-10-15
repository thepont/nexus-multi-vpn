package com.multiregionvpn.core

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var nordVpnApi: NordVpnApiService
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var context: Context
    private lateinit var service: VpnTemplateService

    @Before
    fun setUp() {
        nordVpnApi = Mockito.mock(NordVpnApiService::class.java)
        settingsRepo = Mockito.mock(SettingsRepository::class.java)
        context = Mockito.mock(Context::class.java)

        `when`(context.cacheDir).thenReturn(tempFolder.root)
        service = VpnTemplateService(nordVpnApi, settingsRepo, context)
    }

    @Test
    fun prepareLocalTestConfig_createsAuthFileWithOwnerPermissions() = runBlocking {
        val creds = ProviderCredentials("local-test", "testuser", "testpass")
        `when`(settingsRepo.getProviderCredentials("local-test")).thenReturn(creds)

        val vpnConfig = VpnConfig(
            id = "test_config_id",
            name = "Local Test Config",
            regionId = "US",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1194"
        )

        val prepared = service.prepareConfig(vpnConfig)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText()).isEqualTo("testuser\ntestpass\n")

        assertThat(authFile.canRead()).isTrue()
        assertThat(authFile.canWrite()).isTrue()
    }
}
