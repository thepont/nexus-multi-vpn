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
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var nordVpnApi: NordVpnApiService
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var service: VpnTemplateService

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        nordVpnApi = mock(NordVpnApiService::class.java)
        settingsRepo = mock(SettingsRepository::class.java)

        `when`(context.cacheDir).thenReturn(tempFolder.root)

        service = VpnTemplateService(
            nordVpnApi = nordVpnApi,
            settingsRepo = settingsRepo,
            context = context
        )
    }

    @Test
    fun prepareConfig_localTestTemplate_createsAuthFileWithPermissions() = runBlocking {
        val config = VpnConfig(
            id = "test_tunnel_1",
            name = "Local Test UK",
            regionId = "UK",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1194"
        )

        val credentials = ProviderCredentials(
            templateId = "local-test",
            username = "test_user",
            password = "test_password"
        )

        `when`(settingsRepo.getProviderCredentials("local-test")).thenReturn(credentials)

        val prepared = service.prepareConfig(config)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!

        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText()).isEqualTo("test_user\ntest_password\n")

        // Check file permissions
        assertThat(authFile.canRead()).isTrue()
        assertThat(authFile.canWrite()).isTrue()
    }
}
