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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockNordApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var mockContext: Context
    private lateinit var service: VpnTemplateService

    @Before
    fun setUp() {
        mockNordApi = mock(NordVpnApiService::class.java)
        mockSettingsRepo = mock(SettingsRepository::class.java)
        mockContext = mock(Context::class.java)

        `when`(mockContext.cacheDir).thenReturn(tempFolder.root)
        service = VpnTemplateService(mockNordApi, mockSettingsRepo, mockContext)
    }

    @Test
    fun prepareLocalTestConfig_createsAuthFileWithRestrictedPermissions() = runBlocking {
        val config = VpnConfig(
            id = "test_config_id",
            name = "Local Test Config",
            regionId = "UK",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1194"
        )
        val creds = ProviderCredentials(
            templateId = "local-test",
            username = "test_user",
            password = "test_password"
        )
        `when`(mockSettingsRepo.getProviderCredentials("local-test")).thenReturn(creds)

        val prepared = service.prepareConfig(config)

        assertThat(prepared.authFile).isNotNull()
        val authFile = prepared.authFile!!
        assertThat(authFile.exists()).isTrue()
        assertThat(authFile.readText()).isEqualTo("test_user\ntest_password\n")
        assertThat(authFile.canExecute()).isFalse()
    }
}
