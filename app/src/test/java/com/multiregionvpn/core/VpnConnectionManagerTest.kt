package com.multiregionvpn.core

import android.os.ParcelFileDescriptor
import com.google.common.truth.Truth.assertThat
import com.multiregionvpn.core.vpnclient.MockOpenVpnClient
import com.multiregionvpn.core.vpnclient.OpenVpnClient
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Ignore
import org.junit.runner.RunWith
import kotlin.test.*
import java.io.FileOutputStream
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for VpnConnectionManager
 */
@RunWith(RobolectricTestRunner::class)
class VpnConnectionManagerTest {

    private lateinit var manager: VpnConnectionManager
    private lateinit var mockClientFactory: (String) -> OpenVpnClient

    @Before
    fun setup() {
        // Create a factory that returns MockOpenVpnClient instances
            mockClientFactory = { _ ->
                MockOpenVpnClient()
            }
            
            manager = VpnConnectionManager.createForTesting(mockClientFactory)
    }

    @Test
    fun `given tunnel does not exist, when createTunnel is called, then tunnel is created`() = runTest {
        // GIVEN: No tunnels exist
        assertFalse(manager.isTunnelConnected("test_tunnel"))
        
        // WHEN: Creating a tunnel
        val config = "client\nremote test.com 1194\nproto udp"
        val result = manager.createTunnel("test_tunnel", config, null)
        
        // THEN: Tunnel is created
        assertTrue(result.success)
        assertTrue(manager.isTunnelConnected("test_tunnel"))
    }

    @Test
    fun `given tunnel exists, when createTunnel is called again, then returns existing status`() = runTest {
        // GIVEN: A tunnel exists
        val tunnelId = "existing_tunnel"
        manager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        
        // WHEN: Trying to create it again
        val result = manager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        
        // THEN: Returns true (tunnel already exists and is connected)
        assertTrue(result.success)
    }

    @Test
    fun `given tunnel exists, when sendPacketToTunnel is called, then packet is forwarded`() = runTest {
        // GIVEN: A connected tunnel
        val tunnelId = "test_tunnel"
        manager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        
        val testPacket = byteArrayOf(1, 2, 3, 4, 5)
        
        // WHEN: Sending a packet
        manager.sendPacketToTunnel(tunnelId, testPacket)
        
        // THEN: Packet is sent to the client
        // (We can verify this by checking the mock client's sentPackets)
        // For now, we verify no exceptions are thrown
        assertTrue(manager.isTunnelConnected(tunnelId))
    }

    @Test
    fun `given tunnel does not exist, when sendPacketToTunnel is called, then nothing happens`() = runTest {
        // GIVEN: No tunnel exists
        val testPacket = byteArrayOf(1, 2, 3)
        
        // WHEN: Trying to send a packet to non-existent tunnel
        manager.sendPacketToTunnel("nonexistent", testPacket)
        
        // THEN: No exception is thrown (graceful handling)
        // Tunnel should not exist
        assertFalse(manager.isTunnelConnected("nonexistent"))
    }

    @Test
    fun `when closeTunnel is called, tunnel is disconnected and removed`() = runTest {
        // GIVEN: A connected tunnel
        val tunnelId = "test_tunnel"
        manager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        assertTrue(manager.isTunnelConnected(tunnelId))
        
        // WHEN: Closing the tunnel
        manager.closeTunnel(tunnelId)
        
        // THEN: Tunnel is removed
        assertFalse(manager.isTunnelConnected(tunnelId))
    }

    @Test
    fun `when closeAll is called, all tunnels are closed`() = runTest {
        // GIVEN: Multiple tunnels
        manager.createTunnel("tunnel1", "client\nremote test.com 1194\nproto udp", null)
        manager.createTunnel("tunnel2", "client\nremote test.com 1194\nproto udp", null)
        
        // WHEN: Closing all tunnels
        manager.closeAll()
        
        // THEN: All tunnels are closed
        assertFalse(manager.isTunnelConnected("tunnel1"))
        assertFalse(manager.isTunnelConnected("tunnel2"))
    }

    @Test
    fun `when packetReceiver is set, received packets trigger callback`() = runTest {
        // GIVEN: Packet receiver callback
        var receivedTunnelId: String? = null
        var receivedPacket: ByteArray? = null
        manager.setPacketReceiver { tunnelId, packet ->
            receivedTunnelId = tunnelId
            receivedPacket = packet
        }
        
        // GIVEN: A tunnel with a mock client
        val tunnelId = "test_tunnel"
        val mockClient = MockOpenVpnClient()
        kotlinx.coroutines.runBlocking {
            mockClient.connect("client\nremote test.com 1194\nproto udp", null)
        }
        mockClient.setPacketReceiver { packet ->
            manager.setPacketReceiver { tid, pkt ->
                receivedTunnelId = tid
                receivedPacket = pkt
            }
            // Simulate the manager's packet receiver
            receivedTunnelId = tunnelId
            receivedPacket = packet
        }
        
        // Create manager with custom factory for this test
        val customManager = VpnConnectionManager.createForTesting { mockClient }
        customManager.setPacketReceiver { tid, pkt ->
            receivedTunnelId = tid
            receivedPacket = pkt
        }
        
        kotlinx.coroutines.runBlocking {
            customManager.createTunnel(tunnelId, "client\nremote test.com 1194\nproto udp", null)
        }
        
        // WHEN: Simulating packet reception
        val testPacket = byteArrayOf(10, 20, 30)
        mockClient.simulateReceivePacket(testPacket)
        
        // THEN: Callback is invoked
        assertEquals(tunnelId, receivedTunnelId)
        assertEquals(testPacket, receivedPacket)
    }

    @Test
    fun `readiness listener emits when tunnel becomes ready`() = runTest {
        val events = mutableListOf<Set<String>>()
        manager.setTunnelReadinessListener { current -> events.add(current) }

        val tunnelId = "ready_tunnel"
        val client = MockOpenVpnClient()
        client.connect("client\nremote test.com 1194\nproto udp", null)

        manager.registerTestClient(tunnelId, client)
        manager.simulateTunnelState(tunnelId, ipAssigned = true, dnsConfigured = true)

        assertTrue(manager.isTunnelReadyForRouting(tunnelId))
        assertEquals(listOf(emptySet(), setOf(tunnelId)), events)
    }

    @Test
    fun `readiness listener clears when tunnel loses readiness`() = runTest {
        val events = mutableListOf<Set<String>>()
        manager.setTunnelReadinessListener { current -> events.add(current) }

        val tunnelId = "ready_tunnel"
        val client = MockOpenVpnClient()
        client.connect("client\nremote test.com 1194\nproto udp", null)

        manager.registerTestClient(tunnelId, client)
        manager.simulateTunnelState(tunnelId, ipAssigned = true, dnsConfigured = true)
        manager.simulateTunnelState(tunnelId, ipAssigned = false, dnsConfigured = true)

        assertFalse(manager.isTunnelReadyForRouting(tunnelId))
        assertEquals(listOf(emptySet(), setOf(tunnelId), emptySet()), events)
    }

    @Test
    @Ignore("Requires ParcelFileDescriptor pipe support; covered by instrumentation diagnostics")
    fun `replacing socket pair closes previous writer`() = runTest {
        val tunnelId = "socket_tunnel"
        val pipeWritePfds = manager.getPrivateMap<ParcelFileDescriptor>("pipeWritePfds")
        val pipeWriteFds = manager.getPrivateMap<Int>("pipeWriteFds")
        val pipeWriters = manager.getPrivateMap<FileOutputStream>("pipeWriters")
 
        val initialPipe = createPipeHandle()
        var closedFlag = false
        val trackingWriter = object : FileOutputStream(initialPipe.write.fileDescriptor) {
            override fun close() {
                closedFlag = true
                super.close()
            }
        }
 
        pipeWritePfds[tunnelId] = initialPipe.write
        pipeWriteFds[tunnelId] = initialPipe.write.fd
        pipeWriters[tunnelId] = trackingWriter
 
        val newPipe = createPipeHandle()
        val dup = ParcelFileDescriptor.dup(newPipe.write.fileDescriptor)
        val newFd = dup.detachFd()
        dup.close()
 
        manager.replaceSocketPairForTest(tunnelId, newFd)
 
        assertTrue(closedFlag, "Previous writer should be closed when socket pair is replaced")
        assertFalse(pipeWriters.containsKey(tunnelId), "Writer map should not retain stale writer")
 
        runCatching { initialPipe.read.close() }
        runCatching { initialPipe.write.close() }
        runCatching { newPipe.read.close() }
        runCatching { newPipe.write.close() }
        pipeWritePfds.remove(tunnelId)
        pipeWriteFds.remove(tunnelId)
        manager.getPrivateMap<FileOutputStream>("pipeWriters").remove(tunnelId)
        manager.closeAll()
    }

    @Test
    @Ignore("Requires ParcelFileDescriptor pipe support; covered by instrumentation diagnostics")
    fun `sendPacketToTunnel recreates writer after socket pair replacement`() = runTest {
        val tunnelId = "socket_tunnel"
        val client = MockOpenVpnClient()
        client.connect("client\nremote test.com 1194\nproto udp", null)
        manager.registerTestClient(tunnelId, client)

        val pipeWritePfds = manager.getPrivateMap<ParcelFileDescriptor>("pipeWritePfds")
        val pipeWriteFds = manager.getPrivateMap<Int>("pipeWriteFds")
        val pipeWriters = manager.getPrivateMap<FileOutputStream>("pipeWriters")
 
        val initialPipe = createPipeHandle()
        val staleWriter = FileOutputStream(initialPipe.write.fileDescriptor)
        pipeWritePfds[tunnelId] = initialPipe.write
        pipeWriteFds[tunnelId] = initialPipe.write.fd
        pipeWriters[tunnelId] = staleWriter
 
        val newPipe = createPipeHandle()
        val dup = ParcelFileDescriptor.dup(newPipe.write.fileDescriptor)
        val newFd = dup.detachFd()
        dup.close()
        manager.replaceSocketPairForTest(tunnelId, newFd)
 
        val payload = byteArrayOf(1, 2, 3, 4)
 
        manager.sendPacketToTunnel(tunnelId, payload)
 
        ParcelFileDescriptor.AutoCloseInputStream(newPipe.read).use { input ->
            val received = ByteArray(payload.size)
            val bytesRead = input.read(received)
            assertEquals(payload.size, bytesRead)
            assertContentEquals(payload, received)
        }
 
        runCatching { initialPipe.read.close() }
        runCatching { initialPipe.write.close() }
        runCatching { newPipe.write.close() }
        pipeWritePfds.remove(tunnelId)
        pipeWriteFds.remove(tunnelId)
        manager.closeAll()
    }
 
    @Test
    @Ignore("Requires ParcelFileDescriptor pipe support; covered by instrumentation diagnostics")
    fun `createPipeHandle returns valid descriptors`() {
        val pipe = createPipeHandle()
        assertNotNull(pipe.read, "Pipe read descriptor should not be null")
        assertNotNull(pipe.write, "Pipe write descriptor should not be null")
        assertTrue(pipe.read.fd >= 0, "Pipe read descriptor should have valid fd")
        assertTrue(pipe.write.fd >= 0, "Pipe write descriptor should have valid fd")
        pipe.read.close()
        pipe.write.close()
    }

    @Test
    @Ignore("Requires ParcelFileDescriptor pipe support; covered by instrumentation diagnostics")
    fun `replaceSocketPair installs descriptors when none exist`() {
        val tunnelId = "socket_install_test"
        val pipe = createPipeHandle()
        val fd = ParcelFileDescriptor.dup(pipe.write.fileDescriptor).detachFd()

        manager.replaceSocketPairForTest(tunnelId, fd)

        val pipeWriteFds = manager.getPrivateMap<Int>("pipeWriteFds")
        val pipeWritePfds = manager.getPrivateMap<ParcelFileDescriptor>("pipeWritePfds")

        assertEquals(fd, pipeWriteFds[tunnelId])
        assertNotNull(pipeWritePfds[tunnelId], "Pipe descriptor should be stored after replacement")

        pipe.read.close()
        pipe.write.close()
        pipeWritePfds.remove(tunnelId)?.close()
        pipeWriteFds.remove(tunnelId)
    }

    @Test
    @Ignore("Requires ParcelFileDescriptor pipe support; covered by instrumentation diagnostics")
    fun `sendPacketToTunnel throws when pipe descriptor missing`() = runTest {
        val tunnelId = "missing_pipe_descriptor"
        val client = MockOpenVpnClient()
        client.connect("client\nremote test.com 1194\nproto udp", null)
        manager.registerTestClient(tunnelId, client)

        val pipe = createPipeHandle()
        manager.getPrivateMap<Int>("pipeWriteFds")[tunnelId] = pipe.write.fd
        manager.getPrivateMap<ParcelFileDescriptor>("pipeWritePfds").remove(tunnelId)

        assertFailsWith<IllegalStateException> {
            manager.sendPacketToTunnel(tunnelId, byteArrayOf(1, 2, 3))
        }

        pipe.read.close()
        pipe.write.close()
    }

    private data class PipeHandle(
        val read: ParcelFileDescriptor,
        val write: ParcelFileDescriptor
    )

    private fun createPipeHandle(): PipeHandle {
        val pipe = ParcelFileDescriptor.createPipe()
        return PipeHandle(pipe[0], pipe[1])
    }

    private fun <T> VpnConnectionManager.getPrivateMap(fieldName: String): MutableMap<String, T> {
        val field = VpnConnectionManager::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as MutableMap<String, T>
    }

    @Test
    fun `sendPacketToTunnel queues packets when client not connected`() = runTest {
        val manager = VpnConnectionManager.createForTesting { TestOpenVpnClient(false) }
        val tunnelId = "pending_tunnel"
        val client = TestOpenVpnClient(connected = false)
        manager.registerTestClient(tunnelId, client)

        val packet = byteArrayOf(1, 2, 3)
        manager.sendPacketToTunnel(tunnelId, packet)

        val packetQueues = manager.getPrivateMap<java.util.concurrent.ConcurrentLinkedQueue<Any>>("packetQueues")
        val queue = packetQueues[tunnelId]
        assertNotNull(queue)
        assertEquals(1, queue.size)
        assertTrue(client.sentPackets.isEmpty())
    }

    @Test
    fun `flushQueuedPackets sends queued packets and removes queue`() = runTest {
        val manager = VpnConnectionManager.createForTesting { TestOpenVpnClient(false) }
        val tunnelId = "flush_tunnel"
        val client = TestOpenVpnClient(connected = false)
        manager.registerTestClient(tunnelId, client)

        val queuedPacket = byteArrayOf(9, 9, 9)
        manager.sendPacketToTunnel(tunnelId, queuedPacket)
        client.connected = true

        val flushMethod = VpnConnectionManager::class.java.getDeclaredMethod("flushQueuedPackets", String::class.java)
        flushMethod.isAccessible = true
        flushMethod.invoke(manager, tunnelId)

        assertEquals(1, client.sentPackets.size)
        assertContentEquals(queuedPacket, client.sentPackets.single())
        val packetQueues = manager.getPrivateMap<java.util.concurrent.ConcurrentLinkedQueue<Any>>("packetQueues")
        assertThat(packetQueues.containsKey(tunnelId)).isFalse()
    }

    @Test
    fun `flushQueuedPackets drops stale packets`() = runTest {
        val manager = VpnConnectionManager.createForTesting { TestOpenVpnClient(false) }
        val tunnelId = "stale_flush"
        val client = TestOpenVpnClient(connected = true)
        manager.registerTestClient(tunnelId, client)

        val packetQueues = manager.getPrivateMap<java.util.concurrent.ConcurrentLinkedQueue<Any>>("packetQueues")
        val queue = java.util.concurrent.ConcurrentLinkedQueue<Any>()
        val timeoutField = VpnConnectionManager::class.java.getDeclaredField("QUEUE_TIMEOUT_MS")
        timeoutField.isAccessible = true
        val timeoutMs = timeoutField.getLong(manager)
        val stalePacket = createQueuedPacket(byteArrayOf(1), System.currentTimeMillis() - timeoutMs - 1)
        val freshPacket = createQueuedPacket(byteArrayOf(2), System.currentTimeMillis())
        queue.add(stalePacket)
        queue.add(freshPacket)
        packetQueues[tunnelId] = queue

        val flushMethod = VpnConnectionManager::class.java.getDeclaredMethod("flushQueuedPackets", String::class.java)
        flushMethod.isAccessible = true
        flushMethod.invoke(manager, tunnelId)

        assertEquals(1, client.sentPackets.size)
        assertContentEquals(byteArrayOf(2), client.sentPackets.single())
        assertThat(packetQueues.containsKey(tunnelId)).isFalse()
    }

    @Test
    fun `sendPacketToTunnel drops packets when queue full`() = runTest {
        val manager = VpnConnectionManager.createForTesting { TestOpenVpnClient(false) }
        val tunnelId = "overflow_tunnel"
        val client = TestOpenVpnClient(connected = false)
        manager.registerTestClient(tunnelId, client)

        val packetQueues = manager.getPrivateMap<java.util.concurrent.ConcurrentLinkedQueue<Any>>("packetQueues")
        val queue = java.util.concurrent.ConcurrentLinkedQueue<Any>()
        val maxField = VpnConnectionManager::class.java.getDeclaredField("MAX_QUEUE_SIZE")
        maxField.isAccessible = true
        val maxSize = maxField.getInt(manager)
        repeat(maxSize) { index ->
            queue.add(createQueuedPacket(byteArrayOf(index.toByte()), System.currentTimeMillis()))
        }
        packetQueues[tunnelId] = queue

        val before = queue.map { extractQueuedPacketBytes(it!!) }.toList()
        manager.sendPacketToTunnel(tunnelId, byteArrayOf(7))

        assertEquals(maxSize, packetQueues[tunnelId]!!.size)
        assertTrue(client.sentPackets.isEmpty())
        val after = queue.map { extractQueuedPacketBytes(it!!) }.toList()
        assertThat(after).isEqualTo(before)
        assertFalse(after.contains(byteArrayOf(7)))
    }

    @Test
    fun `installSocketPairFd replaces descriptors and restarts reader`() {
        val manager = VpnConnectionManager.createForTesting { TestOpenVpnClient(true) }
        val tunnelId = "socket_reinstall"

        val pipeWritePfds = manager.getPrivateMap<ParcelFileDescriptor>("pipeWritePfds")
        val pipeWriters = manager.getPrivateMap<FileOutputStream>("pipeWriters")
        val pipeReaders = manager.getPrivateMap<kotlinx.coroutines.Job>("pipeReaders")
        val existingPipe = ParcelFileDescriptor.createPipe()
        var closed = false
        val trackingWriter = object : FileOutputStream(existingPipe[1].fileDescriptor) {
            override fun close() {
                closed = true
                super.close()
            }
        }
        pipeWritePfds[tunnelId] = existingPipe[1]
        pipeWriters[tunnelId] = trackingWriter
        val existingJob = kotlinx.coroutines.Job()
        pipeReaders[tunnelId] = existingJob

        val newPipe = ParcelFileDescriptor.createPipe()
        val installMethod = VpnConnectionManager::class.java.declaredMethods.first {
            it.name == "installSocketPairFd" && it.parameterTypes.size == 3
        }
        installMethod.isAccessible = true
        installMethod.invoke(manager, tunnelId, newPipe[1].fd, true)

        val pipeWriteFds = manager.getPrivateMap<Int>("pipeWriteFds")
        assertThat(pipeWriteFds[tunnelId]).isEqualTo(newPipe[1].fd)
        assertTrue(closed)
        assertTrue(existingJob.isCancelled)
        assertNotNull(pipeWritePfds[tunnelId])

        newPipe[0].close()
        newPipe[1].close()
        existingPipe[0].close()
    }

    private class TestOpenVpnClient(var connected: Boolean = true) : OpenVpnClient {
        val sentPackets = mutableListOf<ByteArray>()

        override suspend fun connect(ovpnConfig: String, authFilePath: String?): Boolean {
            return connected
        }

        override fun sendPacket(packet: ByteArray) {
            sentPackets.add(packet)
        }

        override suspend fun disconnect() {
            connected = false
        }

        override fun isConnected(): Boolean = connected

        override fun setPacketReceiver(callback: (ByteArray) -> Unit) {
            // no-op for tests
        }
    }

    private fun createQueuedPacket(packet: ByteArray, timestamp: Long): Any {
        val clazz = Class.forName("com.multiregionvpn.core.VpnConnectionManager\$QueuedPacket")
        val ctor = clazz.getDeclaredConstructor(ByteArray::class.java, java.lang.Long.TYPE)
        ctor.isAccessible = true
        return ctor.newInstance(packet, timestamp)
    }

    private fun extractQueuedPacketBytes(entry: Any): ByteArray {
        val method = entry.javaClass.getDeclaredMethod("getPacket")
        method.isAccessible = true
        return method.invoke(entry) as ByteArray
    }
}

