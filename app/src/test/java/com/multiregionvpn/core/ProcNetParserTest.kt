package com.multiregionvpn.core

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for ProcNetParser - UID detection from /proc/net files
 */
class ProcNetParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun test_ipToHex_convertsToLittleEndianHex() {
        // Test case: 192.168.1.100
        // Bytes: 192 (0xC0), 168 (0xA8), 1 (0x01), 100 (0x64)
        // Little-endian: 64 01 A8 C0 = "6401A8C0"
        val result = ProcNetParser.ipToHex("192.168.1.100")
        assertThat(result).isEqualTo("6401A8C0")
    }

    @Test
    fun test_ipToHex_convertsLocalhost() {
        // Test case: 127.0.0.1
        // Bytes: 127 (0x7F), 0 (0x00), 0 (0x00), 1 (0x01)
        // Little-endian: 01 00 00 7F = "0100007F"
        val result = ProcNetParser.ipToHex("127.0.0.1")
        assertThat(result).isEqualTo("0100007F")
    }

    @Test
    fun test_portToLittleEndianHex_convertsPort443() {
        // Port 443 = 0x01BB
        // Little-endian: BB 01 = "BB01"
        val result = ProcNetParser.portToLittleEndianHex(443)
        assertThat(result).isEqualTo("BB01")
    }

    @Test
    fun test_portToLittleEndianHex_convertsPort80() {
        // Port 80 = 0x0050
        // Little-endian: 50 00 = "5000"
        val result = ProcNetParser.portToLittleEndianHex(80)
        assertThat(result).isEqualTo("5000")
    }

    @Test
    fun test_portToLittleEndianHex_convertsPort65535() {
        // Port 65535 = 0xFFFF
        // Little-endian: FF FF = "FFFF"
        val result = ProcNetParser.portToLittleEndianHex(65535)
        assertThat(result).isEqualTo("FFFF")
    }

    @Test
    fun test_readUidFromProcNet_parsesTcpConnection() {
        // Create mock /proc/net/tcp file
        val procNetDir = tempFolder.newFolder("proc", "net")
        val tcpFile = File(procNetDir, "tcp")
        
        // Format: sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
        // Example connection: 192.168.1.100:443 -> 127.0.0.1:80, UID 10123
        // 192.168.1.100 = 6401A8C0, 443 = BB01
        // 127.0.0.1 = 0100007F, 80 = 5000
        tcpFile.writeText("""
            sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
              0: 6401A8C0:BB01 0100007F:5000 01 00000000:00000000 00:00000000 00000000  10123        0 54321 2 0000000000000000 100 0 0 10 0
        """.trimIndent())
        
        val uid = ProcNetParser.readUidFromProcNet(
            srcIp = "192.168.1.100",
            srcPort = 443,
            destIp = "127.0.0.1",
            destPort = 80,
            protocol = 6, // TCP
            procNetDir = procNetDir.absolutePath
        )
        
        assertThat(uid).isEqualTo(10123)
    }

    @Test
    fun test_readUidFromProcNet_parsesUdpConnection() {
        val procNetDir = tempFolder.newFolder("proc", "net")
        val udpFile = File(procNetDir, "udp")
        
        // UDP connection: 10.0.0.2:53 -> 8.8.8.8:53, UID 1000
        // 10.0.0.2 = 0200000A, 53 = 3500
        // 8.8.8.8 = 08080808, 53 = 3500
        udpFile.writeText("""
            sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode ref pointer drops
              0: 0200000A:3500 08080808:3500 07 00000000:00000000 00:00000000 00000000  1000        0 98765 2 0000000000000000 0
        """.trimIndent())
        
        val uid = ProcNetParser.readUidFromProcNet(
            srcIp = "10.0.0.2",
            srcPort = 53,
            destIp = "8.8.8.8",
            destPort = 53,
            protocol = 17, // UDP
            procNetDir = procNetDir.absolutePath
        )
        
        assertThat(uid).isEqualTo(1000)
    }

    @Test
    fun test_readUidFromProcNet_returnsNullForUnknownConnection() {
        val procNetDir = tempFolder.newFolder("proc", "net")
        val tcpFile = File(procNetDir, "tcp")
        
        tcpFile.writeText("""
            sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
              0: 6401A8C0:BB01 0100007F:5000 01 00000000:00000000 00:00000000 00000000  10123        0 54321 2 0000000000000000 100 0 0 10 0
        """.trimIndent())
        
        // Query for a different connection
        val uid = ProcNetParser.readUidFromProcNet(
            srcIp = "192.168.1.200", // Different IP
            srcPort = 443,
            destIp = "127.0.0.1",
            destPort = 80,
            protocol = 6,
            procNetDir = procNetDir.absolutePath
        )
        
        assertThat(uid).isNull()
    }

    @Test
    fun test_readUidFromProcNet_returnsNullForUnsupportedProtocol() {
        val uid = ProcNetParser.readUidFromProcNet(
            srcIp = "192.168.1.100",
            srcPort = 443,
            destIp = "127.0.0.1",
            destPort = 80,
            protocol = 1 // ICMP - unsupported
        )
        
        assertThat(uid).isNull()
    }
}

