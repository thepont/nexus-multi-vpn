package com.multiregionvpn.test

import android.util.Log
import java.io.File

/**
 * Helper utilities for Docker Compose testing.
 * 
 * Provides validation and helper methods for test environments.
 */
object DockerComposeTestHelper {
    private const val TAG = "DockerComposeTestHelper"
    
    /**
     * Validates that a Docker Compose file exists and is readable
     */
    fun validateComposeFile(composeFile: DockerComposeManager.ComposeFile): Boolean {
        val composeFilePath = DockerComposeManager.getComposeFilePath(composeFile)
        val file = File(composeFilePath)
        if (!file.exists()) {
            Log.e(TAG, "Docker Compose file not found: $composeFilePath")
            return false
        }
        if (!file.canRead()) {
            Log.e(TAG, "Cannot read Docker Compose file: $composeFilePath")
            return false
        }
        return true
    }
    
    /**
     * Gets the project root directory
     */
    fun getProjectRoot(): File {
        // On Android, user.dir might not be reliable
        // For tests, we can't reliably find project root from Android runtime
        // Just return a safe default - validation will fail gracefully if files don't exist
        try {
            val userDir = System.getProperty("user.dir", ".")
            val current = File(userDir)
            if (current.exists() || current.isAbsolute) {
                // Try to walk up to find project root
                var dir = current
                var lastValid = dir
                var depth = 0
                while (depth < 10) {
                    if (!dir.exists()) break
                    if (File(dir, "gradlew").exists() || File(dir, "settings.gradle.kts").exists()) {
                        return dir
                    }
                    lastValid = dir
                    val parent = dir.parentFile
                    if (parent == null || parent == dir || !parent.exists()) break
                    dir = parent
                    depth++
                }
                if (lastValid.exists()) {
                    return lastValid
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding project root", e)
        }
        // Fallback - return a safe default that won't cause null pointer
        return File("/")
    }
    
    /**
     * Validates OpenVPN server configuration directory exists
     */
    fun validateOpenVpnConfig(configName: String): Boolean {
        val projectRoot = getProjectRoot()
        val configDir = File(projectRoot, "app/src/androidTest/resources/openvpn/$configName")
        if (!configDir.exists()) {
            Log.w(TAG, "OpenVPN config directory not found: ${configDir.absolutePath}")
            Log.w(TAG, "   Run: bash app/src/androidTest/resources/openvpn-configs/generate-server-configs.sh")
            return false
        }
        val serverConf = File(configDir, "server.conf")
        if (!serverConf.exists()) {
            Log.w(TAG, "OpenVPN server.conf not found: ${serverConf.absolutePath}")
            return false
        }
        return true
    }
    
    /**
     * Validates HTTP server content directory exists
     */
    fun validateHttpContent(contentName: String): Boolean {
        val projectRoot = getProjectRoot()
        val httpDir = File(projectRoot, "app/src/androidTest/resources/http-$contentName")
        if (!httpDir.exists()) {
            Log.w(TAG, "HTTP content directory not found: ${httpDir.absolutePath}")
            return false
        }
        val indexHtml = File(httpDir, "index.html")
        if (!indexHtml.exists()) {
            Log.w(TAG, "HTTP index.html not found: ${indexHtml.absolutePath}")
            return false
        }
        return true
    }
    
    /**
     * Prints setup status for a test environment
     */
    fun printSetupStatus(composeFile: DockerComposeManager.ComposeFile) {
        println("\n📋 Setup Status:")
        println("   Docker Compose file: ${if (validateComposeFile(composeFile)) "✅" else "❌"}")
        
        when (composeFile) {
            DockerComposeManager.ComposeFile.ROUTING -> {
                println("   OpenVPN UK config: ${if (validateOpenVpnConfig("uk")) "✅" else "❌"}")
                println("   OpenVPN FR config: ${if (validateOpenVpnConfig("fr")) "✅" else "❌"}")
                println("   HTTP UK content: ${if (validateHttpContent("uk")) "✅" else "❌"}")
                println("   HTTP FR content: ${if (validateHttpContent("fr")) "✅" else "❌"}")
            }
            DockerComposeManager.ComposeFile.DNS -> {
                println("   OpenVPN DNS config: ${if (validateOpenVpnConfig("dns")) "✅" else "❌"}")
                println("   HTTP DNS content: ${if (validateHttpContent("dns")) "✅" else "❌"}")
            }
            DockerComposeManager.ComposeFile.DNS_DOMAIN -> {
                println("   OpenVPN DNS domain config: ${if (validateOpenVpnConfig("dns-domain")) "✅" else "❌"}")
                println("   HTTP DNS domain content: ${if (validateHttpContent("dns-domain")) "✅" else "❌"}")
            }
            DockerComposeManager.ComposeFile.CONFLICT -> {
                println("   OpenVPN UK conflict config: ${if (validateOpenVpnConfig("uk-conflict")) "✅" else "❌"}")
                println("   OpenVPN FR conflict config: ${if (validateOpenVpnConfig("fr-conflict")) "✅" else "❌"}")
                println("   HTTP UK conflict content: ${if (validateHttpContent("uk-conflict")) "✅" else "❌"}")
                println("   HTTP FR conflict content: ${if (validateHttpContent("fr-conflict")) "✅" else "❌"}")
            }
        }
        println()
    }
}

