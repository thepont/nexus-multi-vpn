package com.multiregionvpn.core

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages packet buffering for JIT VPN connections.
 * Buffers packets when a tunnel is not yet connected, then flushes them once connected.
 */
@Singleton
class PacketBufferManager @Inject constructor() {
    private val buffers = ConcurrentHashMap<RouteKey, BufferedFlow>()
    private val mutex = Mutex()
    
    // Memory limits
    private val MAX_BUFFER_SIZE_BYTES = 64 * 1024 // 64 KB per flow
    private val MAX_TOTAL_BUFFER_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB total
    
    /**
     * Key for routing packets to a specific provider/region combination.
     */
    data class RouteKey(
        val appUid: Int,
        val providerId: String,
        val regionCode: String
    )

    /**
     * Represents a buffered flow of packets.
     */
    private class BufferedFlow(
        val routeKey: RouteKey,
        val packets: MutableList<ByteArray> = mutableListOf(),
        var totalBytes: Long = 0L
    ) {
        fun addPacket(packet: ByteArray): Boolean {
            if (packets.size >= MAX_PACKETS_PER_FLOW) {
                return false // Buffer full
            }
            packets.add(packet)
            totalBytes += packet.size
            return true
        }

        fun drain(): List<ByteArray> {
            val result = packets.toList()
            packets.clear()
            totalBytes = 0L
            return result
        }

        fun clear() {
            packets.clear()
            totalBytes = 0L
        }
    }

    /**
     * Enqueues a packet for buffering.
     * Returns true if the packet was buffered, false if it should be dropped.
     */
    suspend fun enqueuePacket(routeKey: RouteKey, packet: ByteArray): Boolean = mutex.withLock {
        val buffer = buffers.getOrPut(routeKey) { BufferedFlow(routeKey) }
        
        // Check total memory limit
        val currentTotalBytes = buffers.values.sumOf { it.totalBytes }
        if (currentTotalBytes + packet.size > MAX_TOTAL_BUFFER_SIZE_BYTES) {
            // Evict oldest flow if we're over limit
            evictOldestFlow()
        }
        
        // Check per-flow limits
        if (buffer.totalBytes + packet.size > MAX_BUFFER_SIZE_BYTES) {
            Log.w(TAG, "Buffer full for $routeKey, dropping packet")
            return false
        }
        
        val added = buffer.addPacket(packet)
        if (added) {
            Log.d(TAG, "Buffered packet for $routeKey (${buffer.packets.size} packets, ${buffer.totalBytes} bytes)")
        }
        return added
    }

    /**
     * Drains all buffered packets for a route.
     * Returns the list of packets that were buffered.
     */
    suspend fun drainPackets(routeKey: RouteKey): List<ByteArray> = mutex.withLock {
        val buffer = buffers.remove(routeKey)
        return buffer?.drain() ?: emptyList()
    }

    /**
     * Clears all buffered packets for a route.
     */
    suspend fun clearRoute(routeKey: RouteKey) = mutex.withLock {
        buffers.remove(routeKey)?.clear()
    }

    /**
     * Gets the current buffer size for a route.
     */
    suspend fun getBufferSize(routeKey: RouteKey): Int = mutex.withLock {
        return buffers[routeKey]?.packets?.size ?: 0
    }

    /**
     * Evicts the oldest flow to make room for new packets.
     */
    private fun evictOldestFlow() {
        if (buffers.isEmpty()) return
        
        // Simple eviction: remove the flow with the most packets (likely oldest)
        val oldest = buffers.maxByOrNull { it.value.packets.size }
        oldest?.let {
            Log.w(TAG, "Evicting buffer for ${it.key} (${it.value.packets.size} packets)")
            buffers.remove(it.key)?.clear()
        }
    }

    companion object {
        private const val TAG = "PacketBufferManager"
        private const val MAX_PACKETS_PER_FLOW = 1000
    }
}

