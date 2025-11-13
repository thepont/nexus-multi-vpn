package com.multiregionvpn

import android.app.Application

/**
 * Base application class for Hilt tests.
 * This does NOT have @HiltAndroidApp - Hilt will generate the test application.
 */
abstract class BaseTestApplication : Application()


