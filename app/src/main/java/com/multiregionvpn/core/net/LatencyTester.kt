package com.multiregionvpn.core.net

import android.util.Log
import com.multiregionvpn.core.provider.ProviderServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Measures latency to VPN servers using ICMP-like methods.
 * Uses TCP handshake time as a proxy for latency when ICMP is unavailable.
 */
@Singleton
class LatencyTester @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /**
     * Result of a latency measurement.
     */
    data class ServerLatency(
        val server: ProviderServer,
        val latencyMs: Long, // Measured latency in milliseconds
        val success: Boolean // Whether the measurement succeeded
    )

    /**
     * Measures latency to a list of servers concurrently.
     * Returns results sorted by latency (fastest first).
     * 
     * @param servers List of servers to test
     * @param timeoutMs Maximum time to wait for each measurement
     * @return List of latency results, sorted by latency (fastest first)
     */
    suspend fun measure(
        servers: List<ProviderServer>,
        timeoutMs: Long = 3000L
    ): List<ServerLatency> = withContext(Dispatchers.IO) {
        if (servers.isEmpty()) {
            return@withContext emptyList()
        }

        Log.d(TAG, "Measuring latency to ${servers.size} server(s)...")

        val results = servers.map { server ->
            async {
                try {
                    val latency = measureLatency(server, timeoutMs)
                    ServerLatency(server, latency, success = true)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to measure latency to ${server.hostname}: ${e.message}")
                    ServerLatency(server, Long.MAX_VALUE, success = false)
                }
            }
        }.awaitAll()

        val sorted = results.sortedBy { it.latencyMs }
        Log.d(TAG, "Latency measurements complete. Fastest: ${sorted.firstOrNull()?.server?.hostname} (${sorted.firstOrNull()?.latencyMs}ms)")
        sorted
    }

    /**
     * Measures latency to a single server.
     * Uses TCP handshake time as a proxy for latency.
     */
    private suspend fun measureLatency(server: ProviderServer, timeoutMs: Long): Long = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        // Try to resolve IP address first
        val ipAddress = server.ipAddress ?: try {
            InetAddress.getByName(server.hostname).hostAddress
        } catch (e: Exception) {
            throw Exception("Failed to resolve hostname: ${e.message}")
        }

        // Use TCP handshake as latency measurement
        // Connect to a common port (443 for HTTPS) and measure connection time
        try {
            val request = Request.Builder()
                .url("https://$ipAddress")
                .head() // HEAD request is faster
                .build()

            val response = withTimeout(timeoutMs) {
                client.newCall(request).execute()
            }
            response.close()

            val latency = System.currentTimeMillis() - startTime
            Log.d(TAG, "Measured latency to ${server.hostname} ($ipAddress): ${latency}ms")
            latency
        } catch (e: Exception) {
            // Fallback: try ICMP ping if available (requires root on most devices)
            // For now, just throw the exception
            throw Exception("Failed to measure latency: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LatencyTester"
    }
}


