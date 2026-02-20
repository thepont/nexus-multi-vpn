package com.multiregionvpn.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.multiregionvpn.data.database.*
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.GeoIpService
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoRuleServiceTest {

    @Test
    fun testRunAutoSetupOptimizedQueryCount() = runTest {
        val context = mockk<Context>(relaxed = true)
        val packageManager = mockk<PackageManager>()
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        val appRuleDao = mockk<AppRuleDao>(relaxed = true)
        val geoIpService = mockk<GeoIpService>()
        val testScope = this

        every { context.packageManager } returns packageManager
        every { settingsRepository.appRuleDao } returns appRuleDao

        // Mock GeoIpService - User is in US
        coEvery { geoIpService.getCurrentRegion() } returns "US"

        // Mock Package Manager - 10 apps installed
        val apps = (1..10).map { i ->
            ApplicationInfo().apply { packageName = "com.app$i" }
        }
        every { packageManager.getInstalledApplications(any<Int>()) } returns apps

        // Mock PresetRules
        val presetRules = apps.map { app ->
            val region = if (app.packageName.endsWith("1")) "UK" else "US"
            PresetRule(app.packageName, region)
        }
        coEvery { settingsRepository.getAllPresetRules() } returns presetRules

        // Mock existing rules and VPN configs for batch fetching
        coEvery { appRuleDao.getAllRulesList() } returns emptyList()
        val vpnUk = VpnConfig("vpn-uk", "UK Server", "UK", "nordvpn", "uk.nordvpn.com")
        every { settingsRepository.getAllVpnConfigs() } returns kotlinx.coroutines.flow.flowOf(listOf(vpnUk))

        // The service being tested
        val service = AutoRuleService(context, settingsRepository, geoIpService, testScope)

        // Act
        service.runAutoSetup()
        advanceUntilIdle()

        // Assert/Verify optimization

        // 1. Individual queries are NO LONGER called inside the loop
        verify(exactly = 0) { settingsRepository.getAppRuleByPackageName(any()) }
        verify(exactly = 0) { settingsRepository.findVpnForRegion(any()) }

        // 2. Batch queries are called instead
        coVerify(exactly = 1) { appRuleDao.getAllRulesList() }
        verify(exactly = 1) { settingsRepository.getAllVpnConfigs() }

        println("Optimization verified: 0 individual queries, batch queries used instead.")
    }
}
