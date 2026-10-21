package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var nordVpnApi: NordVpnApiService
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var context: Context
    private lateinit var cacheDir: File
    private lateinit var vpnTemplateService: VpnTemplateService

    @Before
    fun setUp() {
        nordVpnApi = mock(NordVpnApiService::class.java)
        settingsRepository = mock(SettingsRepository::class.java)
        context = mock(Context::class.java)

        cacheDir = tempFolder.newFolder("cache")
        `when`(context.cacheDir).thenReturn(cacheDir)

        vpnTemplateService = VpnTemplateService(nordVpnApi, settingsRepository, context)
    }

    @Test
    fun prepareNordVpnConfig_createsSecureAuthFile() = runBlocking {
        val config = VpnConfig(
            id = "nord_123",
            name = "NordVPN UK",
            templateId = "nordvpn",
            regionId = "UK",
            serverHostname = "uk123.nordvpn.com"
        )
        val credentials = ProviderCredentials(
            templateId = "nordvpn",
            username = "user123",
            password = "secretpassword"
        )

        `when`(nordVpnApi.getOvpnConfig("uk123.nordvpn.com"))
            .thenReturn("client\ndev tun\nauth-user-pass\n".toResponseBody(null))
        `when`(settingsRepository.getProviderCredentials("nordvpn"))
            .thenReturn(credentials)

        val prepared = vpnTemplateService.prepareConfig(config)

        assertNotNull(prepared.authFile)
        val authFile = prepared.authFile!!
        assertTrue("Auth file should exist", authFile.exists())
        assertEquals("user123\nsecretpassword\n", authFile.readText(Charsets.UTF_8))
    }

    @Test
    fun prepareLocalTestConfig_createsSecureAuthFile() = runBlocking {
        val config = VpnConfig(
            id = "local_456",
            name = "Local FR",
            templateId = "local-test",
            regionId = "FR",
            serverHostname = "127.0.0.1:1194"
        )
        val credentials = ProviderCredentials(
            templateId = "local-test",
            username = "localuser",
            password = "localpass"
        )

        `when`(settingsRepository.getProviderCredentials("local-test"))
            .thenReturn(credentials)

        val prepared = vpnTemplateService.prepareConfig(config)

        assertNotNull(prepared.authFile)
        val authFile = prepared.authFile!!
        assertTrue("Auth file should exist", authFile.exists())
        assertEquals("localuser\nlocalpass\n", authFile.readText(Charsets.UTF_8))
    }
}
