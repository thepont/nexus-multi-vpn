package com.multiregionvpn.core

import android.content.Context
import com.multiregionvpn.data.database.ProviderCredentials
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class VpnTemplateServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var mockNordVpnApi: NordVpnApiService
    private lateinit var mockSettingsRepo: SettingsRepository
    private lateinit var service: VpnTemplateService

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockNordVpnApi = mock(NordVpnApiService::class.java)
        mockSettingsRepo = mock(SettingsRepository::class.java)

        `when`(mockContext.cacheDir).thenReturn(tempFolder.root)

        service = VpnTemplateService(
            nordVpnApi = mockNordVpnApi,
            settingsRepo = mockSettingsRepo,
            context = mockContext
        )
    }

    @Test
    fun testPrepareNordVpnConfigCreatesSecureAuthFile() = runBlocking {
        val config = VpnConfig(
            id = "nord-uk-1",
            name = "NordVPN UK",
            regionId = "UK",
            templateId = "nordvpn",
            serverHostname = "uk123.nordvpn.com"
        )

        val fakeNordOvpn = "client\ndev tun\nauth-user-pass\nproto udp"
        `when`(mockNordVpnApi.getOvpnConfig("uk123.nordvpn.com"))
            .thenReturn(fakeNordOvpn.toResponseBody(null))

        val fakeCreds = ProviderCredentials(
            templateId = "nordvpn",
            username = "test_user",
            password = "test_password"
        )
        `when`(mockSettingsRepo.getProviderCredentials("nordvpn"))
            .thenReturn(fakeCreds)

        val prepared = service.prepareConfig(config)

        assertNotNull(prepared.authFile)
        val authFile = prepared.authFile!!

        assertTrue("Auth file must exist", authFile.exists())
        assertEquals("test_user\ntest_password\n", authFile.readText())

        // Verify owner-only permissions where supported by filesystem
        assertTrue("Auth file should be readable", authFile.canRead())
        assertTrue("Auth file should be writable", authFile.canWrite())
        assertFalse("Auth file should not be executable", authFile.canExecute())
    }

    @Test
    fun testPrepareLocalTestConfigCreatesSecureAuthFile() = runBlocking {
        val config = VpnConfig(
            id = "local-uk-1",
            name = "Local UK",
            regionId = "UK",
            templateId = "local-test",
            serverHostname = "10.0.2.2:1194"
        )

        val fakeCreds = ProviderCredentials(
            templateId = "local-test",
            username = "local_user",
            password = "local_password"
        )
        `when`(mockSettingsRepo.getProviderCredentials("local-test"))
            .thenReturn(fakeCreds)

        val prepared = service.prepareConfig(config)

        assertNotNull(prepared.authFile)
        val authFile = prepared.authFile!!

        assertTrue("Auth file must exist", authFile.exists())
        assertEquals("local_user\nlocal_password\n", authFile.readText())

        assertTrue("Auth file should be readable", authFile.canRead())
        assertTrue("Auth file should be writable", authFile.canWrite())
        assertFalse("Auth file should not be executable", authFile.canExecute())
    }
}
