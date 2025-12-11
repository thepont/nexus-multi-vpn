package com.multiregionvpn

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

import android.util.Log // Import Log

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application {
        Log.d("HiltTestRunner", "newApplication: Before super.newApplication call")
        val app = super.newApplication(cl, HiltTestApplication::class.java.name, context)
        Log.d("HiltTestRunner", "newApplication: After super.newApplication call. App class: ${app.javaClass.name}")
        return app
    }
}
