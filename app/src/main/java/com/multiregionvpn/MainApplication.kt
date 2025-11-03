package com.multiregionvpn

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication : Application() {
    // Hilt will handle all initialization
}
