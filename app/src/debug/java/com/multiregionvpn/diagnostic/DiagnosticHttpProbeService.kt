package com.multiregionvpn.diagnostic

import android.app.IntentService
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.net.HttpURLConnection
import java.net.URL

/**
 * Simple debug-only service that performs an HTTP probe from the app process so that
 * routing logic runs under the real application UID (not the instrumentation runner).
 */
class DiagnosticHttpProbeService : IntentService("DiagnosticHttpProbeService") {

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        val urlString = intent.getStringExtra(EXTRA_URL) ?: return

        val resultIntent = Intent(ACTION_PROBE_RESULT)
        try {
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = intent.getIntExtra(EXTRA_CONNECT_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
                readTimeout = intent.getIntExtra(EXTRA_READ_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
            }
            Log.d(TAG, "Starting HTTP probe for $urlString")

            try {
                connection.connect()
                val responseCode = connection.responseCode
                val responseBody = connection.inputStream.bufferedReader().readText()
                Log.d(TAG, "HTTP probe success. Code=$responseCode, length=${responseBody.length}")

                resultIntent.putExtra(EXTRA_SUCCESS, true)
                resultIntent.putExtra(EXTRA_RESPONSE_CODE, responseCode)
                resultIntent.putExtra(EXTRA_RESPONSE_BODY, responseBody)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP probe failed: ${e.message}", e)
            resultIntent.putExtra(EXTRA_SUCCESS, false)
            resultIntent.putExtra(EXTRA_ERROR_MESSAGE, e.message ?: e::class.java.simpleName)
        } finally {
            LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent)
        }
    }

    companion object {
        private const val TAG = "DiagnosticHttpProbeSvc"
        const val ACTION_PROBE_RESULT = "com.multiregionvpn.diagnostic.PROBE_RESULT"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_CONNECT_TIMEOUT_MS = "extra_connect_timeout"
        const val EXTRA_READ_TIMEOUT_MS = "extra_read_timeout"
        const val EXTRA_SUCCESS = "extra_success"
        const val EXTRA_RESPONSE_CODE = "extra_response_code"
        const val EXTRA_RESPONSE_BODY = "extra_response_body"
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"

        private const val DEFAULT_TIMEOUT_MS = 10_000
    }
}

