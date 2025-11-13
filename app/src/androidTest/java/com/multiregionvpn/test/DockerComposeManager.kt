package com.multiregionvpn.test

import android.util.Log

/**
 * Utility class for managing Docker Compose environments.
 * 
 * IMPORTANT: Docker Compose runs on the HOST MACHINE (development machine), not on Android!
 * 
 * This class provides:
 * 1. Service port mappings (host ports where services are exposed)
 * 2. Validation that Docker Compose files exist
 * 3. Helper methods to get service connection info
 * 
 * Docker Compose should be started manually on the host machine:
 *   cd app/src/androidTest/resources/docker-compose
 *   docker-compose -f docker-compose.routing.yaml up -d
 * 
 * Or via the setup script:
 *   bash app/src/androidTest/resources/setup-test-environment.sh
 */
object DockerComposeManager {
    private const val TAG = "DockerComposeManager"
    
    /**
     * Docker Compose file names (relative to docker-compose directory)
     */
            enum class ComposeFile(val fileName: String) {
                ROUTING("docker-compose.routing.yaml"),
                DNS("docker-compose.dns.yaml"),
                DNS_DOMAIN("docker-compose.dns-domain.yaml"),
                CONFLICT("docker-compose.conflict.yaml")
            }
    
    /**
     * Gets the absolute path to a Docker Compose file
     */
    fun getComposeFilePath(composeFile: ComposeFile): String {
        val projectRoot = System.getProperty("user.dir")
        val relativePath = "app/src/androidTest/resources/docker-compose/${composeFile.fileName}"
        return java.io.File(projectRoot, relativePath).absolutePath
    }
    
    /**
     * Service port mappings (host ports where services are exposed)
     * These are the ports defined in docker-compose YAML files
     */
    enum class ServicePort(val port: Int, val description: String) {
        // Routing test
        VPN_UK(1194, "UK VPN server"),
        VPN_FR(1195, "FR VPN server"),
        HTTP_UK(18080, "UK HTTP server"),
        HTTP_FR(18081, "FR HTTP server"),
        
        // DNS test
        VPN_DNS(1196, "DNS VPN server"),
        HTTP_DNS(8082, "DNS HTTP server"),
        
        // DNS domain test
        VPN_DNS_DOMAIN(1199, "DNS Domain VPN server"),
        HTTP_DNS_DOMAIN(8085, "DNS Domain HTTP server"),
        
        // Conflict test
        VPN_UK_CONFLICT(1197, "UK VPN server (conflict)"),
        VPN_FR_CONFLICT(1198, "FR VPN server (conflict)"),
        HTTP_UK_CONFLICT(8083, "UK HTTP server (conflict)"),
        HTTP_FR_CONFLICT(8084, "FR HTTP server (conflict)")
    }
    
    /**
     * Gets the VPN server hostname for a specific test environment.
     * 
     * @param composeFile The Docker Compose file being used
     * @param tunnelType The type of tunnel (UK, FR, DNS, etc.)
     * @param hostIp The host machine IP (from HostMachineManager)
     * @return VPN server hostname (e.g., "10.0.2.2:1194")
     */
    fun getVpnServerHostname(
        composeFile: ComposeFile,
        tunnelType: String,
        hostIp: String
    ): String {
        val port = when {
            composeFile == ComposeFile.ROUTING && tunnelType.contains("UK", ignoreCase = true) -> 
                ServicePort.VPN_UK.port
            composeFile == ComposeFile.ROUTING && tunnelType.contains("FR", ignoreCase = true) -> 
                ServicePort.VPN_FR.port
            composeFile == ComposeFile.DNS -> 
                ServicePort.VPN_DNS.port
            composeFile == ComposeFile.DNS_DOMAIN -> 
                ServicePort.VPN_DNS_DOMAIN.port
            composeFile == ComposeFile.CONFLICT && tunnelType.contains("UK", ignoreCase = true) -> 
                ServicePort.VPN_UK_CONFLICT.port
            composeFile == ComposeFile.CONFLICT && tunnelType.contains("FR", ignoreCase = true) -> 
                ServicePort.VPN_FR_CONFLICT.port
            else -> {
                Log.w(TAG, "Unknown tunnel type for compose file: $tunnelType")
                1194 // Default
            }
        }
        
        return "$hostIp:$port"
    }
    
    /**
     * Gets the HTTP server URL for a specific test environment.
     * 
     * @param composeFile The Docker Compose file being used
     * @param serverType The type of server (UK, FR, DNS, etc.)
     * @param hostIp The host machine IP (from HostMachineManager)
     * @return HTTP server URL (e.g., "http://10.0.2.2:8080")
     */
    fun getHttpServerUrl(
        composeFile: ComposeFile,
        serverType: String,
        hostIp: String
    ): String {
        val port = when {
            composeFile == ComposeFile.ROUTING && serverType.contains("UK", ignoreCase = true) -> 
                ServicePort.HTTP_UK.port
            composeFile == ComposeFile.ROUTING && serverType.contains("FR", ignoreCase = true) -> 
                ServicePort.HTTP_FR.port
            composeFile == ComposeFile.DNS -> 
                ServicePort.HTTP_DNS.port
            composeFile == ComposeFile.DNS_DOMAIN -> 
                ServicePort.HTTP_DNS_DOMAIN.port
            composeFile == ComposeFile.CONFLICT && serverType.contains("UK", ignoreCase = true) -> 
                ServicePort.HTTP_UK_CONFLICT.port
            composeFile == ComposeFile.CONFLICT && serverType.contains("FR", ignoreCase = true) -> 
                ServicePort.HTTP_FR_CONFLICT.port
            else -> {
                Log.w(TAG, "Unknown server type for compose file: $serverType")
                8080 // Default
            }
        }
        
        return "http://$hostIp:$port"
    }
    
    /**
     * Validates that Docker Compose is running on the host.
     * This checks if services are accessible via their ports.
     * 
     * Note: This is a best-effort check. Actual validation requires
     * connecting to services, which is done during tests.
     * 
     * @param composeFile The Docker Compose file to check
     * @return True if services appear to be running (heuristic check)
     */
    fun validateServicesRunning(composeFile: ComposeFile): Boolean {
        // We can't actually check if Docker is running from Android
        // This is a placeholder - actual validation happens during test execution
        Log.d(TAG, "Cannot validate Docker services from Android - assuming running on host")
        return true
    }
    
    /**
     * Prints setup instructions for starting Docker Compose on the host machine.
     */
    fun printSetupInstructions(composeFile: ComposeFile) {
        Log.i(TAG, """
            |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            |Docker Compose Setup Instructions
            |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            |
            |Docker Compose must be running on the HOST MACHINE (not on Android).
            |
            |To start Docker Compose:
            |  1. On your development machine, run:
            |     cd app/src/androidTest/resources/docker-compose
            |     docker-compose -f ${composeFile.fileName} up -d
            |
            |  2. Or use the setup script:
            |     bash app/src/androidTest/resources/setup-test-environment.sh
            |
            |  3. Verify services are running:
            |     docker-compose -f ${composeFile.fileName} ps
            |
            |  4. For Android emulator, services are accessible at: 10.0.2.2
            |  5. For physical device, use your host machine's IP address
            |
            |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        """.trimMargin())
    }
}
