package com.multiregionvpn.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ConnectionTrackerTest {

    private lateinit var packageManager: PackageManager
    private lateinit var tracker: ConnectionTracker

    @Before
    fun setUp() {
        packageManager = Mockito.mock(PackageManager::class.java)
        tracker = ConnectionTracker(Mockito.mock(Context::class.java), packageManager)
    }

    @Test
    fun mapsPackageToTunnel() {
        mockApp("com.example.app", uid = 10101)

        val registered = tracker.setPackageToTunnel("com.example.app", "nordvpn_UK")

        assertThat(registered).isTrue()
        assertThat(tracker.getCurrentPackageMappings())
            .containsEntry("com.example.app", "nordvpn_UK")
    }

    @Test
    fun clearPackageRemovesMapping() {
        mockApp("com.example.app", uid = 10101)
        tracker.setPackageToTunnel("com.example.app", "nordvpn_UK")

        tracker.clearPackage("com.example.app")

        assertThat(tracker.getCurrentPackageMappings()).isEmpty()
    }

    @Test
    fun clearAllMappingsResetsState() {
        mockApp("com.example.app", uid = 10101)
        mockApp("com.example.second", uid = 10102)
        tracker.setPackageToTunnel("com.example.app", "nordvpn_UK")
        tracker.setPackageToTunnel("com.example.second", "nordvpn_FR")

        tracker.clearAllMappings()

        assertThat(tracker.getCurrentPackageMappings()).isEmpty()
    }

    private fun mockApp(packageName: String, uid: Int) {
        val appInfo = ApplicationInfo().apply {
            this.packageName = packageName
            this.uid = uid
        }
        `when`(packageManager.getApplicationInfo(packageName, 0)).thenReturn(appInfo)
    }
}
