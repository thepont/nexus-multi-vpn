package com.example.diagnostic.fr

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DiagnosticReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        intent.setExtrasClassLoader(ResultReceiver::class.java.classLoader)
        val pendingResult = goAsync()
        val resultAction = intent.getStringExtra(EXTRA_RESULT_ACTION)
        val resultPackage = intent.getStringExtra(EXTRA_RESULT_PACKAGE)
        val receiver = intent.getResultReceiver()
        val url = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_URL

        CoroutineScope(Dispatchers.IO).launch {
            val bundle = Bundle()
            try {
                val start = SystemClock.elapsedRealtime()
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000

                val responseCode = connection.responseCode
                val responseMessage = connection.responseMessage
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val duration = SystemClock.elapsedRealtime() - start
                val remoteIp = try {
                    InetAddress.getByName(connection.url.host).hostAddress
                } catch (t: Throwable) {
                    null
                }

                bundle.putInt(KEY_RESPONSE_CODE, responseCode)
                bundle.putString(KEY_RESPONSE_MESSAGE, responseMessage)
                bundle.putString(KEY_COUNTRY_CODE, COUNTRY_REGEX.find(responseText)?.groupValues?.get(1))
                bundle.putString(KEY_RAW_RESPONSE, responseText)
                bundle.putString(KEY_PACKAGE_NAME, context.packageName)
                bundle.putString(KEY_URL, url)
                bundle.putInt(KEY_CONTENT_LENGTH, connection.contentLength)
                bundle.putLong(KEY_DURATION_MS, duration)
                remoteIp?.let { bundle.putString(KEY_REMOTE_IP, it) }
                sendResult(context, resultAction, resultPackage, receiver, Activity.RESULT_OK, bundle)
            } catch (t: Throwable) {
                bundle.putString(KEY_ERROR, t.message)
                bundle.putString(KEY_ERROR_CLASS, t::class.java.name)
                bundle.putString(KEY_PACKAGE_NAME, context.packageName)
                bundle.putString(KEY_URL, url)
                sendResult(context, resultAction, resultPackage, receiver, Activity.RESULT_CANCELED, bundle)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun Intent.getResultReceiver(): ResultReceiver? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_RESULT_RECEIVER)
        }
    }

    companion object {
        const val EXTRA_RESULT_RECEIVER = "com.example.diagnostic.EXTRA_RESULT_RECEIVER"
        const val EXTRA_URL = "com.example.diagnostic.EXTRA_URL"
        const val EXTRA_RESULT_ACTION = "com.example.diagnostic.EXTRA_RESULT_ACTION"
        const val EXTRA_RESULT_PACKAGE = "com.example.diagnostic.EXTRA_RESULT_PACKAGE"

        const val KEY_RESPONSE_CODE = "responseCode"
        const val KEY_RESPONSE_MESSAGE = "responseMessage"
        const val KEY_COUNTRY_CODE = "countryCode"
        const val KEY_RAW_RESPONSE = "rawResponse"
        const val KEY_ERROR = "error"
        const val KEY_ERROR_CLASS = "errorClass"
        const val KEY_PACKAGE_NAME = "packageName"
        const val KEY_URL = "url"
        const val KEY_CONTENT_LENGTH = "contentLength"
        const val KEY_DURATION_MS = "durationMs"
        const val KEY_REMOTE_IP = "remoteIp"
        const val KEY_RESULT_CODE = "resultCode"

        private const val DEFAULT_URL = "http://ip-api.com/json"
        private val COUNTRY_REGEX = Regex("\"countryCode\":\"([A-Z]{2})\"")

        private fun sendResult(
            context: Context,
            resultAction: String?,
            resultPackage: String?,
            receiver: ResultReceiver?,
            resultCode: Int,
            bundle: Bundle
        ) {
            if (!resultAction.isNullOrEmpty() && !resultPackage.isNullOrEmpty()) {
                val intent = Intent(resultAction)
                    .setPackage(resultPackage)
                    .putExtra(KEY_RESULT_CODE, resultCode)
                    .putExtras(Bundle(bundle))
                context.sendBroadcast(intent)
            } else {
                receiver?.send(resultCode, bundle)
            }
        }
    }
}
