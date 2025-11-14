package com.multiregionvpn.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.database.AppRule
import com.multiregionvpn.data.database.VpnConfig
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.ui.MainActivity
import com.multiregionvpn.ui.shared.VpnStats
import com.multiregionvpn.ui.shared.VpnStatus
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Service that extends VpnService.
 * Routes all device traffic and manages packet routing.
 */
@AndroidEntryPoint
open class VpnEngineService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var vpnTemplateService: VpnTemplateService
    
    private lateinit var packetRouter: PacketRouter
    private var connectionTracker: ConnectionTracker? = null
    private var vpnOutput: FileOutputStream? = null
    private val activeTunnels = mutableSetOf<String>() // Track tunnel IDs to avoid duplicates
    private var statsJob: Job? = null
    private var connectionStartTime: Long? = null
    private var connectionManager: VpnConnectionManager? = null
    private var readyTunnelIds: Set<String> = emptySet()
    private var hasEverHadReadyTunnel = false
    private var tunReaderJob: Job? = null
    
    // Multi-IP support: Track tunnel IP addresses per tunnel and subnet
    data class TunnelIpAddress(
        val tunnelId: String,
        val ip: String,
        val prefixLength: Int,
        val subnet: String  // Calculated subnet (e.g., "10.100.0.0/16")
    )
    
    data class TunnelDnsServers(
        val tunnelId: String,
        val dnsServers: List<String>  // DNS server IP addresses from OpenVPN DHCP
    )
    
    data class RouteSubnet(
        val address: String,
        val prefixLength: Int,
        val isIpv6: Boolean
    )
    
    private val tunnelIps = mutableMapOf<String, TunnelIpAddress>()  // tunnelId -> IP address
    private val tunnelDnsServers = mutableMapOf<String, TunnelDnsServers>()  // tunnelId -> DNS servers
    private val tunnelRoutes = mutableMapOf<String, MutableList<RouteSubnet>>() // tunnelId -> pushed routes
    private val subnetToPrimaryTunnel = mutableMapOf<String, String>()  // subnet -> primary tunnelId
    private val tunnelTemplateIds = mutableMapOf<String, String>() // tunnelId -> templateId
    private var shouldReestablishInterface = false  // Flag to trigger interface re-establishment
    private var currentAllowedPackages = emptySet<String>()  // Track current allowed apps for split tunneling
    
    // Network change monitoring
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    override fun onCreate() {
        super.onCreate()
        runningInstance = this  // Set static reference for socket protection
        createNotificationChannel()
        // Initialize VpnConnectionManager early so it's available throughout service lifetime
        // This ensures getInstance() calls don't fail
        try {
            VpnConnectionManager.initialize(this, this)
            Log.d(TAG, "VpnConnectionManager initialized in onCreate()")
        } catch (e: Exception) {
            Log.w(TAG, "VpnConnectionManager already initialized or error: ${e.message}")
        }
        
        // Register network change listener to detect Wi-Fi <-> 4G switches
        registerNetworkCallback()
    }

    private fun configureConnectionManager() {
        if (connectionManager != null) {
            return
        }
        connectionManager = try {
            VpnConnectionManager.getInstance()
        } catch (e: IllegalStateException) {
            VpnConnectionManager.initialize(this, this)
        }
        val manager = connectionManager ?: return

        manager.setTunnelIpCallback { tunnelId, ip, prefixLength ->
            onTunnelIpReceived(tunnelId, ip, prefixLength)
        }

        manager.setTunnelDnsCallback { tunnelId, dnsServers ->
            onTunnelDnsReceived(tunnelId, dnsServers)
        }

        manager.setTunnelRouteCallback { tunnelId, address, prefixLength, isIpv6 ->
            onTunnelRouteReceived(tunnelId, address, prefixLength, isIpv6)
        }

        manager.setConnectionStateListener { hasConnecting ->
            handleConnectionStateChange(hasConnecting)
        }

        manager.setTunnelReadinessListener { readyIds ->
            handleTunnelReadinessChange(readyIds)
        }
    }

    private fun handleConnectionStateChange(hasConnecting: Boolean) {
        val newPauseState = hasConnecting
        if (newPauseState != shouldPauseTunReading) {
            val oldPauseState = shouldPauseTunReading
            shouldPauseTunReading = newPauseState

            if (newPauseState) {
                // Connections are connecting - pause TUN reading
                Log.i(TAG, "⏸️  Stopping TUN reading (exclusive access for OpenVPN 3 TLS handshake)")
                Log.i(TAG, "   OpenVPN 3 now has exclusive TUN FD access for connection")
            } else {
                // All connections are established - resume TUN reading
                Log.i(TAG, "✅ OpenVPN 3 connections established - RESUMING TUN reading (socket pair architecture)")
                Log.i(TAG, "   Changed from paused=$oldPauseState to paused=$newPauseState")
                Log.i(TAG, "   OpenVPN 3 reads from socket pairs, not TUN - no conflict")
                Log.i(TAG, "   We read from TUN and route to socket pairs via sendPacketToTunnel()")
            }
        }
    }

    private fun startTunReader(reason: String) {
        if (vpnInterface == null) {
            Log.w(TAG, "⚠️  Cannot start TUN reader (reason=$reason) - vpnInterface is null")
            return
        }

        val existingJob = tunReaderJob
        if (existingJob?.isActive == true) {
            Log.i(TAG, "ℹ️  TUN reader already active (reason=$reason)")
            return
        }

        if (existingJob != null && !existingJob.isCancelled && !existingJob.isCompleted) {
            Log.w(TAG, "⚠️  Cancelling stale TUN reader job before restart (reason=$reason)")
            existingJob.cancel()
        }

        Log.i(TAG, "▶️  Starting TUN reader (reason=$reason)")
        val job = serviceScope.launch {
            Log.i(TAG, "🟢 TUN reader coroutine launched (reason=$reason)")
            try {
                readPacketsFromTun()
            } catch (ce: CancellationException) {
                Log.i(TAG, "🛑 TUN reader coroutine cancelled (reason=$reason, message=${ce.message})")
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "❌ TUN reader coroutine failed (reason=$reason)", e)
                throw e
            } finally {
                Log.i(TAG, "🔚 TUN reader coroutine finished (reason=$reason)")
            }
        }
        job.invokeOnCompletion { throwable ->
            Log.i(
                TAG,
                "📘 TUN reader completion (reason=$reason, " +
                    "status=${throwable?.javaClass?.simpleName ?: "success"})"
            )
            if (tunReaderJob == job) {
                tunReaderJob = null
            }
        }
        tunReaderJob = job
    }

    @VisibleForTesting
    internal fun handleTunnelReadinessChange(readyIds: Set<String>) {
        serviceScope.launch {
            readyTunnelIds = readyIds
            if (readyIds.isNotEmpty()) {
                hasEverHadReadyTunnel = true
                VpnServiceStateTracker.updateStatus(VpnStatus.CONNECTED)
            } else {
                VpnServiceStateTracker.updateStatus(VpnStatus.CONNECTING)
                if (hasEverHadReadyTunnel) {
                    Log.w(TAG, "🚨 All tunnels lost - shutting down VPN interface to avoid zombie state")
                    hasEverHadReadyTunnel = false
                    onAllTunnelsLost()
                }
            }
        }
    }

    protected open fun onAllTunnelsLost() {
        stopVpn()
    }
    
    private fun registerPackageForRouting(packageName: String, vpnConfigId: String) {
        kotlinx.coroutines.runBlocking {
            registerPackageForRoutingInternal(packageName, vpnConfigId)
        }
    }

    private suspend fun registerPackageForRoutingInternal(
        packageName: String,
        vpnConfigId: String
    ): Boolean {
        Log.w(TAG, "VPN[register:start] package=$packageName config=$vpnConfigId trackerReady=${connectionTracker != null}")
        if (connectionTracker == null) {
            Log.d(TAG, "ConnectionTracker not ready when registering $packageName, waiting...")
        }
        return try {
            var attempts = 0
            while (connectionTracker == null && attempts < 50) {
                delay(100)
                attempts++
            }
            val tracker = connectionTracker
            if (tracker == null) {
                Log.w(TAG, "ConnectionTracker still null after waiting when registering $packageName")
                Log.w(TAG, "VPN[register:fail] package=$packageName reason=tracker-null")
                return false
            }
            val vpnConfig = settingsRepository.getVpnConfigById(vpnConfigId)
            if (vpnConfig == null) {
                Log.w(TAG, "Cannot register $packageName - VPN config $vpnConfigId not found")
                Log.w(TAG, "VPN[register:fail] package=$packageName reason=config-missing id=$vpnConfigId")
                return false
            }
            val tunnelId = "${vpnConfig.templateId}_${vpnConfig.regionId}"
            val registered = tracker.setPackageToTunnel(packageName, tunnelId)
            Log.w(TAG, "VPN[register] package=$packageName uid=${tracker.getUidForPackage(packageName)} tunnel=$tunnelId registered=$registered")
            if (registered) {
                Log.i(TAG, "🔁 Updated routing: $packageName → $tunnelId")
            } else {
                Log.w(TAG, "⚠️  Failed to register package $packageName for tunnel $tunnelId (package missing?)")
            }
            registered
        } catch (e: Exception) {
            Log.e(TAG, "Error registering package $packageName", e)
            false
        }
    }

    private fun removePackageRouting(packageName: String) {
        serviceScope.launch {
            try {
                if (connectionTracker == null) {
                    Log.d(TAG, "ConnectionTracker not ready when clearing $packageName, skipping")
                    return@launch
                }
                connectionTracker?.clearPackage(packageName)
                Log.i(TAG, "🧹 Cleared routing for $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear routing for $packageName", e)
            }
        }
    }

    private fun clearAllPackageRoutings() {
        serviceScope.launch {
            try {
                if (connectionTracker == null) {
                    Log.d(TAG, "ConnectionTracker not ready when clearing all mappings, skipping")
                    return@launch
                }
                connectionTracker?.clearAllMappings()
                tunnelRoutes.clear()
                restoreTunnelIpMappings()
                Log.i(TAG, "🧹 Cleared all package routings")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear all package routings", e)
            }
        }
    }

    private fun restoreTunnelIpMappings() {
        val tracker = connectionTracker ?: return
        tunnelIps.forEach { (tunnelId, ipInfo) ->
            try {
                val inetAddress = InetAddress.getByName(ipInfo.ip)
                tracker.setTunnelForIp(inetAddress, tunnelId)
                Log.v(TAG, "🔁 Restored IP→tunnel mapping ${ipInfo.ip} → $tunnelId")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️  Unable to restore IP mapping for ${ipInfo.ip} on $tunnelId: ${e.message}")
            }
        }
        restoreTunnelRouteMappings()
    }

    private fun restoreTunnelRouteMappings() {
        val tracker = connectionTracker ?: return
        tunnelRoutes.forEach { (tunnelId, routes) ->
            routes.forEach { route ->
                try {
                    val inetAddress = InetAddress.getByName(route.address)
                    tracker.addRouteForTunnel(tunnelId, inetAddress, route.prefixLength)
                    Log.v(TAG, "🔁 Restored route ${route.address}/${route.prefixLength} → $tunnelId")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️  Unable to restore route ${route.address}/${route.prefixLength} for $tunnelId: ${e.message}")
                }
            }
        }
    }

    private fun ensureLocalTestRoutes(tunnelId: String) {
        val templateId = tunnelTemplateIds[tunnelId]
        Log.i(
            TAG,
            "ensureLocalTestRoutes: tunnelId=$tunnelId templateId=$templateId trackerNull=${connectionTracker == null} mapKeys=${tunnelTemplateIds.keys}"
        )
        if (templateId != "local-test") return
        if (connectionTracker == null) {
            Log.w(TAG, "Local-test route setup skipped: connectionTracker null for $tunnelId")
            return
        }
        applyLocalTestRoutesIfNeeded(tunnelId)
    }

    private fun applyLocalTestRoutesIfNeeded(tunnelId: String) {
        val templateId = tunnelTemplateIds[tunnelId]
        Log.i(
            TAG,
            "applyLocalTestRoutesIfNeeded: tunnelId=$tunnelId templateId=$templateId knownTemplates=${tunnelTemplateIds.toMap()}"
        )
        if (templateId != "local-test") return

        val routes = when {
            tunnelId.endsWith("UK") -> listOf("198.18.1.0" to 24)
            tunnelId.endsWith("FR") -> listOf("198.18.2.0" to 24)
            else -> emptyList()
        }
        if (routes.isEmpty()) return

        val tracker = connectionTracker ?: return
        val routeList = tunnelRoutes.getOrPut(tunnelId) { mutableListOf() }

        routes.forEach { (address, prefix) ->
            try {
                val inetAddress = InetAddress.getByName(address)
                tracker.addRouteForTunnel(tunnelId, inetAddress, prefix)
                if (routeList.none { it.address == address && it.prefixLength == prefix && !it.isIpv6 }) {
                    routeList.add(RouteSubnet(address, prefix, false))
                }
                Log.i(TAG, "   🧭 Applied local-test route $address/$prefix to $tunnelId")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️  Unable to apply local-test route $address/$prefix to $tunnelId: ${e.message}")
            }
        }
    }

    private suspend fun restorePackageTunnelMappings(appRules: List<AppRule>) {
        val tracker = connectionTracker ?: run {
            Log.w(TAG, "⚠️  ConnectionTracker unavailable when restoring package mappings")
            return
        }
        Log.i(TAG, "♻️ Restoring package→tunnel mappings for ${appRules.size} app rule(s)")
        appRules.forEach { rule ->
            val vpnConfigId = rule.vpnConfigId ?: return@forEach
            val vpnConfig = try {
                settingsRepository.getVpnConfigById(vpnConfigId)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️  Unable to fetch VPN config $vpnConfigId for ${rule.packageName}: ${e.message}")
                null
            } ?: return@forEach

            val tunnelId = "${vpnConfig.templateId}_${vpnConfig.regionId}"
            val uid = tracker.registerPackage(rule.packageName)
            if (uid != null) {
                tracker.setUidToTunnel(uid, tunnelId)
                Log.i(TAG, "🔁 Restored package mapping ${rule.packageName} (uid=$uid) → $tunnelId")
            } else {
                Log.w(TAG, "⚠️  Unable to restore package mapping for ${rule.packageName} (UID lookup failed)")
            }
        }
    }

    private fun initializePacketRouter() {
        // Initialize VpnConnectionManager with Context and VpnService for real OpenVPN clients
        val connectionManager = VpnConnectionManager.initialize(this, this)
        
        // Set TUN file descriptor in VpnConnectionManager if available
        // CRITICAL: We must duplicate the FD instead of detaching it
        // Detaching makes vpnInterface.fileDescriptor invalid, causing EBADF when reading packets
        vpnInterface?.let { pfd ->
            try {
                // Duplicate the ParcelFileDescriptor so both VpnEngineService and OpenVPN 3 can use it
                // ParcelFileDescriptor.dup() creates a new PFD pointing to the same file descriptor
                val duplicatedPfd = pfd.dup()
                
                // Get the integer FD from the duplicated ParcelFileDescriptor using detachFd()
                // Since we have the original pfd still, we can safely detach from the duplicate
                val duplicatedFd = duplicatedPfd.detachFd()
                
                if (duplicatedFd >= 0) {
                    // Pass both the FD and the original PFD so VpnConnectionManager can duplicate it per connection
                    connectionManager.setTunFileDescriptor(duplicatedFd, pfd)
                    Log.d(TAG, "Base TUN file descriptor set in VpnConnectionManager: $duplicatedFd")
                    Log.d(TAG, "   Original PFD stored for per-connection duplication")
                    Log.d(TAG, "   Each OpenVPN connection will get its own duplicated FD")
                } else {
                    Log.w(TAG, "duplicatedPfd.detachFd() returned invalid FD: $duplicatedFd")
                    duplicatedPfd.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not duplicate TUN file descriptor: ${e.message}")
                Log.e(TAG, "   This will cause connection failures. Stack trace:", e)
            }
        }
        
        // Set up packet receiver to write packets from tunnels back to TUN interface
        connectionManager.setPacketReceiver { tunnelId, packet ->
            try {
                // Check if this is a DNS response (UDP packet, likely from port 53)
                val isDnsResponse = packet.size > 20 && packet[9].toInt() and 0xFF == 17 // UDP protocol
                if (isDnsResponse) {
                    // Parse to check if it's from port 53
                    try {
                        val buffer = java.nio.ByteBuffer.wrap(packet).order(java.nio.ByteOrder.BIG_ENDIAN)
                        val ipHeaderLength = ((buffer.get(0).toInt() and 0xFF) and 0x0F) * 4
                        if (packet.size >= ipHeaderLength + 4) {
                            buffer.position(ipHeaderLength)
                            val srcPort = buffer.short.toInt() and 0xFFFF
                            if (srcPort == 53) {
                                Log.d(TAG, "📥 DNS response received from tunnel $tunnelId (${packet.size} bytes from port 53)")
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore parsing errors
                    }
                }
                
                vpnOutput?.write(packet)
                vpnOutput?.flush()
                // PERFORMANCE: Removed per-packet logging to prevent binder exhaustion
                if (isDnsResponse) {
                    Log.d(TAG, "📥 DNS response received from tunnel $tunnelId (${packet.size} bytes)")
                }
                // Removed verbose logging for non-DNS packets to prevent binder flood
            } catch (e: Exception) {
                Log.e(TAG, "Error writing packet from tunnel $tunnelId to TUN", e)
            }
        }
        
        // Set up tunnel IP address callback to receive DHCP-assigned IPs
        connectionManager.setTunnelIpCallback { tunnelId, ip, prefixLength ->
            onTunnelIpReceived(tunnelId, ip, prefixLength)
        }
        
        // Set up tunnel DNS callback to receive DNS servers from DHCP options
        connectionManager.setTunnelDnsCallback { tunnelId, dnsServers ->
            onTunnelDnsReceived(tunnelId, dnsServers)
        }
        
        // Set up connection state listener for event-based exclusive TUN access
        connectionManager.setConnectionStateListener { hasConnecting ->
            val newPauseState = hasConnecting
            if (newPauseState != shouldPauseTunReading) {
                val oldPauseState = shouldPauseTunReading
                shouldPauseTunReading = newPauseState
                
                if (newPauseState) {
                    // Connections are connecting - pause TUN reading
                    Log.i(TAG, "⏸️  Stopping TUN reading (exclusive access for OpenVPN 3 TLS handshake)")
                    Log.i(TAG, "   OpenVPN 3 now has exclusive TUN FD access for connection")
                } else {
                    // All connections are established - resume TUN reading
                    // CRITICAL: With socket pairs, OpenVPN 3 reads from socket pairs (NOT from TUN),
                    // so there's no conflict. We MUST resume TUN reading to route packets.
                    // 
                    // Architecture with socket pairs:
                    // - App → TUN → readPacketsFromTun() → PacketRouter → socket pair → OpenVPN 3
                    // - OpenVPN 3 → socket pair → pipe reader → packetReceiver → TUN → App
                    // 
                    // OpenVPN 3 does NOT read from TUN when using socket pairs, so we can safely
                    // read from TUN and route packets to socket pairs without conflicts.
                    Log.i(TAG, "✅ OpenVPN 3 connections established - RESUMING TUN reading (socket pair architecture)")
                    Log.i(TAG, "   Changed from paused=$oldPauseState to paused=$newPauseState")
                    Log.i(TAG, "   OpenVPN 3 reads from socket pairs, not TUN - no conflict")
                    Log.i(TAG, "   We read from TUN and route to socket pairs via sendPacketToTunnel()")
                }
            }
        }
        
        // Create connection tracker for UID detection (alternative to /proc/net)
        if (connectionTracker == null) {
            connectionTracker = ConnectionTracker(this, packageManager)
        }
        
        // CRITICAL: Register all packages with app rules so ConnectionTracker knows about them
        // In Global VPN mode, we don't use addAllowedApplication(), so ConnectionTracker
        // needs to be explicitly told which packages to track for routing
        serviceScope.launch {
            try {
                val appRules = settingsRepository.getAllAppRules().first()
                Log.i(TAG, "📝 Registering ${appRules.size} packages with ConnectionTracker for VPN routing")
                appRules.forEach { appRule ->
                    val vpnConfigId = appRule.vpnConfigId
                    if (vpnConfigId != null) {
                        registerPackageForRouting(appRule.packageName, vpnConfigId)
                    }
                }
                Log.i(TAG, "✅ Package registration complete - ConnectionTracker ready for routing")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to register packages with ConnectionTracker", e)
            }
        }
        
        PacketRouter.resetDebugCounters()
        packetRouter = PacketRouter(
            this,
            settingsRepository,
            this,
            connectionManager,
            vpnOutput,
            connectionTracker
        )
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() called with action: ${intent?.action}")
        
        // Ensure VpnConnectionManager is initialized (in case onCreate() wasn't called)
        // This can happen if the service is already running or is being reused
        try {
            VpnConnectionManager.initialize(this, this)
            Log.d(TAG, "VpnConnectionManager initialized in onStartCommand()")
        } catch (e: Exception) {
            // Already initialized or error - that's fine
            Log.v(TAG, "VpnConnectionManager already initialized or error: ${e.message}")
        }
        
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Received ACTION_START - starting VPN...")
                startVpn()
            }
            ACTION_STOP -> {
                Log.i(TAG, "Received ACTION_STOP - stopping VPN...")
                stopVpn()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startVpn() {
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        Log.i(TAG, "🚀 startVpn() called")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        
        VpnServiceStateTracker.updateStatus(VpnStatus.CONNECTING)
        configureConnectionManager()
        
        if (vpnInterface != null) {
            Log.w(TAG, "VPN already started")
            VpnServiceStateTracker.updateStatus(VpnStatus.CONNECTED)
            startStatsUpdates()
            return
        }
        
        // Call startForeground FIRST before any long-running operations
        // This prevents the "ForegroundServiceDidNotStartInTimeException"
        try {
            Log.d(TAG, "Starting foreground service notification...")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            Log.d(TAG, "✅ Foreground notification started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground notification", e)
            Log.e(TAG, "Error details: ${e.message}")
            // Continue anyway - might be a race condition or permission issue
            // But log it so we know what went wrong
        }
        
        try {
            // Get all app rules to determine which apps should use VPN
            // This implements proper split tunneling - only apps with rules use VPN
            // CRITICAL: Use direct database query (not Flow.first()) to ensure we get
            // committed data, not stale Flow emission
            val appRules = kotlinx.coroutines.runBlocking {
                settingsRepository.appRuleDao.getAllRulesList()
            }
            Log.i(TAG, "📋 App rules found: ${appRules.size}")
            appRules.forEach { rule ->
                Log.i(TAG, "   📱 ${rule.packageName} → ${rule.vpnConfigId ?: "Direct Internet"}")
            }
            Log.e(TAG, "VPN DEBUG: startVpn collected ${appRules.size} app rule(s)")
            
            // Get unique package names that have VPN rules
            val packagesWithRules = appRules
                .filter { it.vpnConfigId != null }
                .map { it.packageName }
                .distinct()
            
            Log.d(TAG, "App rules found: ${appRules.size}, packages with VPN rules: ${packagesWithRules.size}")
            
            // CRITICAL: For proper split tunneling, if no apps have rules, do NOT establish VPN interface
            // This ensures NO traffic goes through VPN when no apps are selected
            if (packagesWithRules.isEmpty()) {
                Log.w(TAG, "⚠️  No app rules found - NOT establishing VPN interface")
                Log.w(TAG, "   This is correct split tunneling: no apps = no VPN interface = no VPN traffic")
                Log.w(TAG, "   VPN interface will be established automatically when rules are added")
                // Still start tunnel management to monitor for new rules
                serviceScope.launch {
                    manageTunnels(emptyMap())
                }
                Log.i(TAG, "✅ VPN service started (interface will be established when rules are added)")
                VpnServiceStateTracker.updateStatus(VpnStatus.DISCONNECTED)
                stopStatsUpdates()
                return
            }
            
            // CRITICAL: Pre-fetch VPN configs BEFORE establishing VPN interface
            // When VPN interface is established, DNS is routed through VPN
            // But VPN isn't connected yet, causing DNS resolution failures
            // Solution: Fetch configs first, then establish interface
            Log.d(TAG, "Pre-fetching VPN configs before establishing VPN interface...")
            val activeVpnConfigIds = appRules
                .filter { it.vpnConfigId != null }
                .map { it.vpnConfigId!! }
                .distinct()
            
            val configsPrepared = mutableMapOf<String, PreparedVpnConfig>()
            val vpnConfigById = mutableMapOf<String, VpnConfig>()
            for (vpnConfigId in activeVpnConfigIds) {
                try {
                    val vpnConfig = kotlinx.coroutines.runBlocking {
                        settingsRepository.getVpnConfigById(vpnConfigId)
                    }
                    if (vpnConfig != null) {
                        vpnConfigById[vpnConfigId] = vpnConfig
                        Log.d(TAG, "Pre-fetching config for ${vpnConfig.name}...")
                        val preparedConfig = try {
                            kotlinx.coroutines.runBlocking {
                                vpnTemplateService.prepareConfig(vpnConfig)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to pre-fetch config for ${vpnConfig.name}: ${e.message}")
                            // Continue with other configs - we'll try again later
                            null
                        }
                        if (preparedConfig != null) {
                            configsPrepared[vpnConfigId] = preparedConfig
                            Log.d(TAG, "✅ Pre-fetched config for ${vpnConfig.name}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error pre-fetching config for $vpnConfigId", e)
                }
            }
            
            // Establish VPN interface with split tunneling (only for apps with rules)
            establishVpnInterface(packagesWithRules)
            
            // Bootstrap ConnectionTracker mappings for all rules immediately
            val trackerInstance = connectionTracker ?: ConnectionTracker(this, packageManager).also {
                connectionTracker = it
            }
            appRules.forEach { rule ->
                val vpnConfigId = rule.vpnConfigId ?: return@forEach
                val vpnConfig = vpnConfigById[vpnConfigId] ?: kotlinx.coroutines.runBlocking {
                    settingsRepository.getVpnConfigById(vpnConfigId)
                } ?: return@forEach
                val tunnelId = "${vpnConfig.templateId}_${vpnConfig.regionId}"
                tunnelTemplateIds[tunnelId] = vpnConfig.templateId
                val registered = trackerInstance.setPackageToTunnel(rule.packageName, tunnelId)
                Log.w(TAG, "VPN[bootstrap] package=${rule.packageName} tunnel=$tunnelId registered=$registered")
                ensureLocalTestRoutes(tunnelId)
            }
            
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface even though rules exist")
                stopForeground(true)
                stopSelf()
                return
            }
            
            // Create output stream for writing packets back to TUN interface
            vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
            Log.d(TAG, "VPN output stream created")
            
            // Initialize packet router with the output stream
            // This will also set the TUN file descriptor in VpnConnectionManager
            Log.d(TAG, "Initializing packet router (this initializes VpnConnectionManager)...")
            initializePacketRouter()
            Log.i(TAG, "✅ Packet router initialized - VpnConnectionManager should now be available")
            
            // Foreground notification was already started above
            // Update it if needed
            try {
                startForeground(NOTIFICATION_ID, createNotification())
            } catch (e: Exception) {
                // Already started, that's fine
                Log.v(TAG, "Foreground already started")
            }
            
            // Start packet reading (only if interface is established)
            // CRITICAL: We MUST keep reading to support multi-tunnel routing
            // (different apps → different VPN connections)
            // This may create race conditions with OpenVPN 3, but is necessary
            // for the multi-tunnel architecture to work.
            startTunReader("startVpn-initial")
            
            // Start tunnel management - monitors app rules and creates tunnels
            // Pass pre-fetched configs to avoid DNS issues
            serviceScope.launch {
                manageTunnels(configsPrepared)
            }
            
            Log.i(TAG, "✅ VPN engine started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopSelf()
        }
    }
    
    /**
     * Establishes the VPN interface with proper split tunneling configuration.
     * Only apps in packagesWithRules will be allowed to use the VPN.
     * If packagesWithRules is empty, the interface is NOT established (proper split tunneling).
     */
    private fun establishVpnInterface(packagesWithRules: List<String>) {
        // We now rely on Android's per-package split tunnelling instead of forcing global mode.
        // Only packages with explicit rules are routed through the VPN; everything else stays direct.
        val useGlobalMode = false
        
        if (packagesWithRules.isEmpty() && !useGlobalMode) {
            Log.w(TAG, "⚠️  No app rules found - NOT establishing VPN interface")
            Log.w(TAG, "   This is correct split tunneling behavior: no apps = no VPN interface = no VPN traffic")
            return
        }
        
        if (vpnInterface != null) {
            Log.d(TAG, "VPN interface already established")
            return
        }
        
        if (useGlobalMode) {
            Log.i(TAG, "🌐 USING GLOBAL VPN MODE (for E2E test compatibility)")
            Log.i(TAG, "   All traffic enters VPN interface")
            Log.i(TAG, "   PacketRouter handles per-app routing to correct tunnels")
        }
        
        Log.i(TAG, "Creating VPN interface builder...")
        Log.i(TAG, "Packages with VPN rules: $packagesWithRules")
        
        val builder = Builder()
        builder.setSession("MultiRegionVPN")

        // TODO: Use IP address from OpenVPN DHCP instead of hardcoding
        // Currently, we establish VPN interface BEFORE OpenVPN connects, so we don't know
        // what IP address OpenVPN will assign via PUSH_REPLY (ifconfig-push).
        // OpenVPN pushes IP via tun_builder_add_address() (e.g., "10.100.0.2/16"), but we
        // ignore it because the interface is already established.
        // 
        // For multi-tunnel setup, this is problematic because:
        // - Different tunnels may get different IP addresses (10.100.0.2, 10.101.0.2, etc.)
        // - We're using a single hardcoded IP for all tunnels
        // - This could cause routing confusion if tunnels use different subnets
        //
        // Solutions (same as DNS):
        // 1. Wait for OpenVPN to connect and receive IP before establishing interface (async flow)
        // 2. Re-establish interface with correct IP after OpenVPN connects (disruptive)
        // 3. Use callback to update VpnService.Builder before establish()
        // For now, using hardcoded IP as fallback (must be compatible with all tunnel subnets)
        // Establish interface with primary tunnel IPs (one per subnet)
        // If no tunnel IPs are available yet, use fallback IP
        if (subnetToPrimaryTunnel.isNotEmpty()) {
            // Use primary tunnel IPs (one per subnet)
            val addedSubnets = mutableSetOf<String>()
            subnetToPrimaryTunnel.values.forEach { primaryTunnelId ->
                val tunnelIp = tunnelIps[primaryTunnelId]
                if (tunnelIp != null && !addedSubnets.contains(tunnelIp.subnet)) {
                    builder.addAddress(tunnelIp.ip, tunnelIp.prefixLength)
                    addedSubnets.add(tunnelIp.subnet)
                    Log.d(TAG, "✅ Added primary tunnel IP: ${tunnelIp.ip}/${tunnelIp.prefixLength} for tunnel $primaryTunnelId (subnet: ${tunnelIp.subnet})")
                }
            }
            Log.i(TAG, "VPN interface established with ${addedSubnets.size} IP address(es) from ${subnetToPrimaryTunnel.size} subnet(s)")
        } else {
            // Fallback: Use hardcoded IP if no tunnel IPs available yet
            // TODO: Remove this once all tunnels have received their IPs
            builder.addAddress("10.0.0.2", 30)
            Log.d(TAG, "Using fallback IP: 10.0.0.2/30 (no tunnel IPs available yet)")
        }
        
        if (!useGlobalMode) {
            // SPLIT TUNNELING: Only apps with VPN rules use the VPN
            // Apps without rules bypass VPN entirely (use normal internet)
            Log.i(TAG, "🔒 Adding ${packagesWithRules.size} app(s) to VPN allowed list:")
            packagesWithRules.forEach { packageName ->
                try {
                    builder.addAllowedApplication(packageName)
                    Log.i(TAG, "   ✅ ALLOWED: $packageName")
                } catch (e: Exception) {
                    Log.e(TAG, "   ❌ FAILED to allow $packageName: ${e.message}")
                }
            }
            Log.i(TAG, "✅ Split tunneling configured: ${packagesWithRules.size} app(s) use VPN")
            Log.i(TAG, "   Apps WITHOUT rules bypass VPN entirely (normal internet)")
        } else {
            // GLOBAL VPN MODE: All traffic enters VPN
            // PacketRouter will handle routing to correct tunnels
            Log.i(TAG, "🌐 Global VPN mode: ALL traffic will enter VPN interface")
            Log.i(TAG, "   No split tunneling at OS level")
            Log.i(TAG, "   PacketRouter handles all routing decisions")
        }
        
        // CRITICAL: We need routes for traffic to go through VPN, but adding them
        // before VPN connects creates a routing loop. Solution: Add routes but
        // protect OpenVPN sockets. However, Android's VpnService.Builder doesn't
        // support modifying routes after establish(). 
        //
        // Alternative: Add route but use protect() for OpenVPN control channel.
        // Actually, OpenVPN 3 should handle this internally, but we can also
        // add the route - Android will handle it correctly for split tunneling.
        // The key is that split tunneling (addAllowedApplication) only affects
        // those apps, not our own app's OpenVPN connection.
        //
        // CRITICAL: Add route for ALL traffic (0.0.0.0/0) to ensure DNS queries
        // from allowed apps go through the VPN interface. Without this, DNS
        // queries might bypass the VPN interface and use system DNS directly.
        builder.addRoute("0.0.0.0", 0) // Route all traffic for allowed apps only (including DNS)
        Log.d(TAG, "✅ Added route 0.0.0.0/0 for allowed apps (this routes DNS queries through VPN interface)")
        
        // Set DNS servers for VPN (only used by allowed apps)
        // Use DNS servers from OpenVPN DHCP options (received via tun_builder_set_dns_options())
        // For multi-tunnel setup, use DNS servers from primary tunnels
        val dnsServersToUse = linkedSetOf<String>()

        // Collect DNS servers from all tunnels (not just primary) - we want all available DNS servers
        // This ensures DNS resolution works even if some tunnels haven't connected yet
        tunnelDnsServers.values.forEach { tunnelDns ->
            tunnelDns.dnsServers.forEach { dnsServer ->
                if (dnsServersToUse.add(dnsServer)) {
                    Log.d(TAG, "✅ Added DNS server from tunnel ${tunnelDns.tunnelId}: $dnsServer")
                }
            }
        }

        if (dnsServersToUse.isEmpty()) {
            Log.w(TAG, "⚠️  No DNS servers available yet - using fallback DNS servers: ${DEFAULT_FALLBACK_DNS_SERVERS.joinToString(", ")}")
            DEFAULT_FALLBACK_DNS_SERVERS.forEach { fallback ->
                if (dnsServersToUse.add(fallback)) {
                    Log.i(TAG, "   ✅ Added fallback DNS server: $fallback")
                }
            }
        }
        
        // CRITICAL: Android's VpnService requires DNS servers to be set for DNS queries
        // to go through the VPN interface. If no DNS servers are set, Android will use
        // system DNS which bypasses the VPN interface entirely.
        dnsServersToUse.forEach { dnsServer ->
            builder.addDnsServer(dnsServer)
            Log.d(TAG, "   ➕ Added DNS server: $dnsServer")
        }
        Log.i(TAG, "✅✅✅ DNS servers configured for VPN interface: ${dnsServersToUse.joinToString(", ")}")
        Log.i(TAG, "   This ensures DNS queries from allowed apps go through VPN interface")
        
        builder.setMtu(1500)
        
        // CRITICAL: Set underlying networks to null to ensure Android DNS resolver uses VPN
        // Without this, Android's DNS resolver may bypass the VPN and use system DNS directly
        // This is especially important for API level 22+ (Lollipop MR1+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
            builder.setUnderlyingNetworks(null)
            Log.d(TAG, "✅ setUnderlyingNetworks(null) - DNS will use VPN interface only")
        }
        
        // Check VPN permission before establishing interface
        Log.d(TAG, "Checking VPN permission with VpnService.prepare()...")
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            Log.e(TAG, "❌ VPN permission not granted - prepare() returned Intent: $prepareIntent")
            Log.e(TAG, "   This means user needs to grant VPN permission manually")
            Log.e(TAG, "   AppOps permission might not be sufficient - need user interaction")
            return
        }
        Log.d(TAG, "✅ VPN permission granted (prepare() returned null)")
        
        Log.d(TAG, "Establishing VPN interface...")
        Log.d(TAG, "NOTE: If this fails, VPN permission may not be granted")
        try {
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "❌ Failed to establish VPN interface")
                Log.e(TAG, "This usually means:")
                Log.e(TAG, "  1. VPN permission was not granted")
                Log.e(TAG, "  2. Another VPN is already active")
                Log.e(TAG, "  3. System resources unavailable")
                Log.e(TAG, "  4. VpnService.prepare() was not called or user denied permission")
                VpnServiceStateTracker.updateStatus(VpnStatus.ERROR)
                stopStatsUpdates()
                return
            }
            Log.i(TAG, "✅ VPN interface established successfully")
            Log.d(TAG, "VPN interface ParcelFileDescriptor: ${if (vpnInterface != null) "valid" else "null"}")
            VpnServiceStateTracker.updateStatus(VpnStatus.CONNECTED)
            startStatsUpdates()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception while establishing VPN interface: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            VpnServiceStateTracker.updateStatus(VpnStatus.ERROR)
            stopStatsUpdates()
            return
        }
        
        Log.i(TAG, "✅ VPN interface established with split tunneling")
    }
    
    /**
     * Graceful shutdown of VPN service.
     * 
     * CRITICAL: This must be called in the correct order to prevent "zombie service" bug:
     * 1. Stop C++ tunnels FIRST (so they stop reading/writing to FD)
     * 2. Close VPN interface SECOND (breaks the "black hole")
     * 3. Stop foreground service THIRD
     * 4. Call stopSelf() LAST
     * 
     * THE BUG: If we call stopSelf() before closing vpnInterface, the VpnService
     * stays active as a "zombie" - the "key" icon remains and all traffic is
     * routed to a black hole, blocking all internet.
     */
    private fun stopVpn() {
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.i(TAG, "🛑 SHUTDOWN: Graceful VPN shutdown initiated...")
        Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        VpnServiceStateTracker.updateStatus(VpnStatus.DISCONNECTED)
        stopStatsUpdates()
        
        try {
            // STEP 1: Tell C++ to stop all tunnels and clean up
            // This stops it from reading/writing to the FD
            Log.i(TAG, "SHUTDOWN Step 1/4: Closing all tunnels...")
            try {
                // Use runBlocking to ensure tunnels are closed BEFORE we close the interface
                kotlinx.coroutines.runBlocking {
                    try {
                        VpnConnectionManager.getInstance().closeAll()
                        Log.i(TAG, "   ✅ All tunnels closed")
                    } catch (e: IllegalStateException) {
                        // VpnConnectionManager not initialized yet - that's okay
                        Log.i(TAG, "   ℹ️  VpnConnectionManager not initialized, skipping tunnel cleanup")
                    }
                }
                activeTunnels.clear()
                updateActiveConnectionCount()
                connectionTracker?.clearAllMappings()
                tunnelRoutes.clear()
                tunnelTemplateIds.clear()
                Log.i(TAG, "   ✅ Connection tracker cleared")
            } catch (e: Exception) {
                Log.e(TAG, "   ❌ Error closing tunnels (continuing with shutdown)", e)
            }
            
            // STEP 2: Close the output stream
            Log.i(TAG, "SHUTDOWN Step 2/4: Closing VPN output stream...")
            try {
                vpnOutput?.close()
                vpnOutput = null
                Log.i(TAG, "   ✅ VPN output stream closed")
            } catch (e: Exception) {
                Log.e(TAG, "   ❌ Error closing vpnOutput (continuing with shutdown)", e)
            }
            
            // STEP 3: Close the TUN interface
            // THIS IS THE CRITICAL FIX: It breaks the "black hole" and tells
            // the OS to stop routing traffic to it
            Log.i(TAG, "SHUTDOWN Step 3/4: Closing VPN interface...")
            try {
                vpnInterface?.close()
                vpnInterface = null
                Log.i(TAG, "   ✅ VPN interface closed - traffic no longer blocked!")
            } catch (e: Exception) {
                Log.e(TAG, "   ❌ Error closing vpnInterface", e)
            }
            
            // STEP 4: Stop foreground service and remove notification
            Log.i(TAG, "SHUTDOWN Step 4/4: Stopping foreground service...")
            try {
                stopForeground(true)
                Log.i(TAG, "   ✅ Foreground service stopped, notification removed")
            } catch (e: Exception) {
                Log.e(TAG, "   ❌ Error stopping foreground", e)
            }
            
            // STEP 5: Tell Android to stop this service
            // This will remove the "key" icon
            Log.i(TAG, "SHUTDOWN Final Step: Calling stopSelf()...")
            stopSelf()
            
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "✅ SHUTDOWN COMPLETE: VPN gracefully stopped, internet restored")
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        } catch (e: Exception) {
            Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.e(TAG, "❌ SHUTDOWN ERROR: Exception during VPN stop", e)
            Log.e(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            // Still try to stop the service even if cleanup failed
            try {
                stopForeground(true)
                stopSelf()
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to stop service after error", e2)
            }
        }
    }

    /**
     * Register network callback to detect network changes (Wi-Fi <-> 4G switches).
     * 
     * THE ZOMBIE TUNNEL BUG:
     * When the device switches networks (e.g., Wi-Fi -> 4G), the OpenVPN/WireGuard client's
     * socket becomes dead. The OS keeps sending packets to our VpnService, but they go to a
     * "black hole." The user loses all connectivity but the app still shows "CONNECTED".
     * 
     * THE FIX:
     * 1. onAvailable(): Call setUnderlyingNetworks() to route our sockets through the new network
     * 2. onAvailable(): Call nativeOnNetworkChanged() to tell C++ router to reconnect tunnels
     */
    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "═══════════════════════════════════════════════════════")
                    Log.i(TAG, "🌐 NETWORK CHANGE DETECTED: onAvailable()")
                    Log.i(TAG, "   Network: $network")
                    Log.i(TAG, "═══════════════════════════════════════════════════════")
                    
                    // CRITICAL STEP 1: Route all sockets created by this service through the new network
                    // This ensures OpenVPN/WireGuard sockets use the new network instead of the old (dead) one
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        try {
                            setUnderlyingNetworks(arrayOf(network))
                            Log.i(TAG, "✅ setUnderlyingNetworks() called - sockets will use new network")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to set underlying networks", e)
                        }
                    }
                    
                    // CRITICAL STEP 2: Notify both Kotlin and C++ layers to reconnect all active tunnels
                    // This ensures both OpenVPN (C++) and WireGuard (Kotlin) tunnels are reconnected
                    
                    // 2a. Reconnect WireGuard tunnels (handled in Kotlin)
                    try {
                        serviceScope.launch {
                            VpnConnectionManager.getInstance().reconnectAllTunnels()
                        }
                        Log.i(TAG, "✅ reconnectAllTunnels() called - WireGuard will reconnect")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to reconnect Kotlin-managed tunnels", e)
                    }
                    
                    // 2b. Reconnect OpenVPN tunnels (handled in C++)
                    try {
                        nativeOnNetworkChanged()
                        Log.i(TAG, "✅ nativeOnNetworkChanged() called - OpenVPN C++ will reconnect")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to notify native layer of network change", e)
                    }
                }
                
                override fun onLost(network: Network) {
                    Log.w(TAG, "🌐 NETWORK LOST: $network")
                }
            }
            
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
            Log.i(TAG, "✅ Network callback registered")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to register network callback", e)
        }
    }
    
    /**
     * Unregister network callback when service is destroyed.
     */
    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let { callback ->
                connectivityManager?.unregisterNetworkCallback(callback)
                Log.i(TAG, "✅ Network callback unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to unregister network callback", e)
        }
    }
    
    /**
     * Native function to notify C++ router of network changes.
     * This will be implemented in the JNI layer (openvpn_jni.cpp).
     */
    private external fun nativeOnNetworkChanged()
    
    override fun onDestroy() {
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        Log.i(TAG, "🛑 onDestroy() called - VPN service shutting down")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        
        // Unregister network callback
        unregisterNetworkCallback()
        
        stopStatsUpdates()
        VpnServiceStateTracker.updateStatus(VpnStatus.DISCONNECTED)
        connectionTracker?.clearAllMappings()
        runningInstance = null
        super.onDestroy()
    }
    
    /**
     * Reads packets from TUN interface and routes them based on app rules.
     * 
     * CRITICAL: Uses event-based exclusive TUN access during connection phase.
     * 
     * Connection Phase (hasConnecting=true):
     * - Completely stop reading from TUN (exclusive access for OpenVPN 3)
     * - OpenVPN 3 needs exclusive TUN access to complete TLS handshake
     * - Wait in loop until connections are established
     * 
     * Connected Phase (hasConnecting=false):
     * - Resume reading to route packets based on app rules
     * - Enable multi-tunnel routing (different apps → different VPNs)
     * 
     * This is event-based: connectionStateListener callback controls pause/resume.
     */
    private var shouldPauseTunReading = false
    
    private suspend fun readPacketsFromTun() {
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        Log.i(TAG, "📖 readPacketsFromTun() STARTING")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        
        val vpnInput = vpnInterface?.let { pfd ->
            Log.d(TAG, "   VPN interface exists, FD: ${pfd.fileDescriptor}")
            FileInputStream(pfd.fileDescriptor)
        } ?: run {
            Log.e(TAG, "❌ Cannot read packets - VPN interface not established (vpnInterface is null)")
            return
        }
        
        Log.d(TAG, "   ✅ TUN input stream created successfully")
        val buffer = ByteArray(32767)
        
        Log.i(TAG, "📖 Starting TUN packet reading loop (event-based exclusive access)")
        Log.d(TAG, "   shouldPauseTunReading: $shouldPauseTunReading")
        
        var packetCount = 0
        var pauseCount = 0
        
        while (vpnInterface != null && serviceScope.isActive) {
            try {
                // Event-based exclusive access: if connections are connecting,
                // completely stop reading (not just pause) to give OpenVPN 3 exclusive TUN access
                if (shouldPauseTunReading) {
                    pauseCount++
                    if (pauseCount % 100 == 0) {
                        Log.d(TAG, "   ⏸️  TUN reading paused (shouldPauseTunReading=true) - waiting... (pause check #$pauseCount)")
                    }
                    // Wait until connections are established (event-based resume)
                    kotlinx.coroutines.delay(100)
                    continue
                }
                
                if (pauseCount > 0) {
                    Log.i(TAG, "   ▶️  TUN reading RESUMED (shouldPauseTunReading=false) after $pauseCount pause checks")
                    pauseCount = 0
                }
                
                    // CRITICAL: Don't log every read attempt - generates millions of log lines
                    // Only log actual packets and errors
                    val length = vpnInput.read(buffer)

                    if (length > 0) {
                        packetCount++
                        val packet = buffer.copyOf(length)
                        Log.i(TAG, "📦 [Packet #$packetCount] Read ${length} bytes from TUN - routing to PacketRouter")
                        // Pass packet to PacketRouter for routing based on app rules
                        // This enables multi-tunnel routing (different apps → different VPNs)
                        packetRouter.routePacket(packet)
                        Log.d(TAG, "   ✅ Packet #$packetCount routed to PacketRouter")
                    } else if (length == -1) {
                        Log.w(TAG, "❌ TUN input stream closed (EOF) - readPacketsFromTun() stopping (read $packetCount packets)")
                        break
                    } else if (length == 0) {
                        // Don't log - this happens constantly and fills log buffer
                        // Log.v(TAG, "   No data (length=0), continuing...")
                    }
            } catch (e: Exception) {
                if (vpnInterface != null) {
                    Log.e(TAG, "❌ Error reading packet from TUN (read $packetCount packets so far)", e)
                    Log.e(TAG, "   Exception type: ${e.javaClass.simpleName}, message: ${e.message}")
                    e.printStackTrace()
                }
                break
            }
        }
        Log.i(TAG, "📖 readPacketsFromTun() coroutine stopped (read $packetCount packets total)")
    }
    
    private suspend fun startInboundLoop() {
        // This loop is no longer needed - packets from tunnels are written
        // directly via the packet receiver callback set in initializePacketRouter()
        // We keep this as a placeholder for future enhancements
        while (vpnInterface != null && serviceScope.isActive) {
            try {
                kotlinx.coroutines.delay(1000)
            } catch (e: Exception) {
                if (vpnInterface != null) {
                    Log.e(TAG, "Error in inbound loop", e)
                }
                break
            }
        }
    }
    
    /**
     * Monitors app rules and manages VPN tunnels.
     * Creates tunnels for VPN configs referenced by app rules,
     * and closes tunnels when they're no longer needed.
     * 
     * @param preFetchedConfigs Optional map of pre-fetched configs to avoid DNS issues
     *                         when VPN interface is already established.
     */
    private suspend fun manageTunnels(preFetchedConfigs: Map<String, PreparedVpnConfig> = emptyMap()) {
        Log.d(TAG, "manageTunnels() coroutine started")
        
        try {
            val connectionManager = VpnConnectionManager.getInstance()
            Log.d(TAG, "VpnConnectionManager instance obtained")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "VpnConnectionManager not initialized in manageTunnels()", e)
            return
        }
        
        val connectionManager = VpnConnectionManager.getInstance()
        
        // Collect all app rules and monitor changes
        Log.i(TAG, "🔄 Starting to collect app rules via Flow...")
        Log.i(TAG, "   Flow should emit whenever app_rules table changes")
        Log.w(TAG, "VPN[manage] collecting app rules flow...")
        settingsRepository.getAllAppRules().collect { appRules ->
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.i(TAG, "🔔 FLOW EMISSION: App rules collected: ${appRules.size} rules found")
            appRules.forEach { rule ->
                Log.i(TAG, "   📱 ${rule.packageName} → ${rule.vpnConfigId ?: "Direct"}")
            }
            Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            connectionTracker?.clearTransientMappings()
            restoreTunnelIpMappings()
            restorePackageTunnelMappings(appRules)
            for (rule in appRules) {
                val vpnConfigId = rule.vpnConfigId ?: continue
                val registered = registerPackageForRoutingInternal(rule.packageName, vpnConfigId)
                println("VPN[manage] rule ${rule.packageName} -> ${rule.vpnConfigId} registered=$registered")
            }
            val snapshot = connectionTracker?.getCurrentPackageMappings() ?: emptyMap()
            Log.i(TAG, "📊 ConnectionTracker mappings after refresh: $snapshot")
            println("VPN[snapshot] $snapshot")
            
            // Get packages with VPN rules
            val packagesWithRules = appRules
                .filter { it.vpnConfigId != null }
                .map { it.packageName }
                .distinct()
                .toSet()
            
            // CRITICAL: Detect if allowed apps changed - must restart interface!
            // addAllowedApplication() is only applied at establish() time
            // Changes to app rules require interface restart to update allowed apps
            if (vpnInterface != null && packagesWithRules != currentAllowedPackages) {
                Log.w(TAG, "⚠️  Allowed apps changed - RESTARTING VPN interface")
                Log.d(TAG, "   Old: $currentAllowedPackages")
                Log.d(TAG, "   New: $packagesWithRules")
                
                // Close current interface
                vpnInterface?.close()
                vpnInterface = null
                vpnOutput?.close()
                vpnOutput = null
                
                // Re-establish with new allowed apps
                if (packagesWithRules.isNotEmpty()) {
                    try {
                        establishVpnInterface(packagesWithRules.toList())
                        if (vpnInterface != null) {
                            vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
                            initializePacketRouter()
                            startTunReader("allowed-apps-updated")
                            currentAllowedPackages = packagesWithRules
                            Log.i(TAG, "✅ VPN interface restarted with updated allowed apps")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restart VPN interface", e)
                        return@collect
                    }
                } else {
                    currentAllowedPackages = emptySet()
                }
            }
            
            // If VPN interface is not established but we have rules, establish it now
            if (vpnInterface == null && packagesWithRules.isNotEmpty()) {
                Log.i(TAG, "App rules detected - establishing VPN interface for split tunneling")
                try {
                    establishVpnInterface(packagesWithRules.toList())
                    currentAllowedPackages = packagesWithRules
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to establish VPN interface when rules detected", e)
                    return@collect
                }
            }
            
            // If VPN interface is established but no rules exist, close it (proper split tunneling)
            if (vpnInterface != null && packagesWithRules.isEmpty()) {
                Log.i(TAG, "No app rules found - closing VPN interface (proper split tunneling)")
                try {
                    vpnInterface?.close()
                    vpnInterface = null
                    vpnOutput?.close()
                    vpnOutput = null
                    currentAllowedPackages = emptySet()
                    // Re-initialize packet router (will handle null interface)
                    initializePacketRouter()
                    Log.i(TAG, "✅ VPN interface closed (no apps need VPN routing)")
                    stopStatsUpdates()
                    VpnServiceStateTracker.updateStatus(VpnStatus.DISCONNECTED)
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing VPN interface", e)
                }
                return@collect
            }
            
            // If VPN interface is not established but we have rules, establish it now
            if (vpnInterface == null && packagesWithRules.isNotEmpty()) {
                Log.i(TAG, "App rules detected - establishing VPN interface for split tunneling")
                try {
                    establishVpnInterface(packagesWithRules.toList())
                    currentAllowedPackages = packagesWithRules
                    if (vpnInterface != null) {
                        // Interface was established - set up streams and router
                        vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
                        initializePacketRouter()
                        startTunReader("interface-established-flow")
                        Log.i(TAG, "✅ VPN interface established and packet reading started")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to establish VPN interface when rules detected", e)
                    return@collect
                }
            }
            
            if (vpnInterface == null) {
                // VPN interface not established and no rules - nothing to do
                Log.d(TAG, "No VPN interface and no rules - waiting for rules to be added")
                return@collect
            }
            
            try {
                // Get all unique VPN config IDs from active rules
                val activeVpnConfigIds = appRules
                    .filter { it.vpnConfigId != null }
                    .map { it.vpnConfigId!! }
                    .distinct()
                
                Log.d(TAG, "Active VPN config IDs from rules: $activeVpnConfigIds")
                
                if (activeVpnConfigIds.isEmpty()) {
                    Log.d(TAG, "No active VPN configs found in app rules")
                    return@collect
                }
                
                // Create tunnels for all active VPN configs
                for (vpnConfigId in activeVpnConfigIds) {
                    val tunnelId = getTunnelId(vpnConfigId)
                    Log.d(TAG, "Processing tunnel for vpnConfigId=$vpnConfigId, tunnelId=$tunnelId")
                    
                    // CRITICAL: Populate connection tracker with app rules for this tunnel
                    // This allows PacketRouter to route packets to the correct tunnel
                    val appRulesForConfig = appRules.filter { it.vpnConfigId == vpnConfigId }
                    appRulesForConfig.forEach { appRule ->
                        connectionTracker?.setPackageToTunnel(appRule.packageName, tunnelId)
                        Log.d(TAG, "Registered package ${appRule.packageName} -> tunnel $tunnelId in connection tracker")
                    }
                    
                    // Skip if tunnel already exists
                    if (activeTunnels.contains(tunnelId) || connectionManager.isTunnelConnected(tunnelId)) {
                        Log.d(TAG, "Tunnel $tunnelId already exists or connected, skipping")
                        continue
                    }
                    
                    // Get VPN config
                    val vpnConfig = settingsRepository.getVpnConfigById(vpnConfigId)
                    if (vpnConfig == null) {
                        Log.w(TAG, "VPN config $vpnConfigId not found, skipping tunnel creation")
                        continue
                    }
                    
                    Log.d(TAG, "Found VPN config: ${vpnConfig.name} (${vpnConfig.serverHostname})")
                    tunnelTemplateIds[tunnelId] = vpnConfig.templateId
                    ensureLocalTestRoutes(tunnelId)
                    
                    // Prepare config using VpnTemplateService
                    // Use pre-fetched config if available (to avoid DNS issues)
                    val preparedConfig = try {
                        if (preFetchedConfigs.containsKey(vpnConfigId)) {
                            Log.d(TAG, "Using pre-fetched config for tunnel $tunnelId")
                            preFetchedConfigs[vpnConfigId]!!
                        } else {
                            Log.d(TAG, "Preparing VPN config for tunnel $tunnelId...")
                            try {
                                vpnTemplateService.prepareConfig(vpnConfig)
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ Failed to prepare VPN config for tunnel $tunnelId", e)
                                // Create and broadcast config error
                                val configError = VpnError.fromException(e, tunnelId).copy(
                                    type = when {
                                        e.message?.contains("credential", ignoreCase = true) == true ||
                                        e.message?.contains("auth", ignoreCase = true) == true -> {
                                            VpnError.ErrorType.AUTHENTICATION_FAILED
                                        }
                                        e.message?.contains("404", ignoreCase = true) == true ||
                                        e.message?.contains("not found", ignoreCase = true) == true ||
                                        e.message?.contains("fetch", ignoreCase = true) == true -> {
                                            VpnError.ErrorType.CONFIG_ERROR
                                        }
                                        else -> VpnError.ErrorType.CONFIG_ERROR
                                    },
                                    details = "Failed to fetch or prepare OpenVPN configuration file. " +
                                            "The server hostname may be incorrect or the server may be unavailable."
                                )
                                broadcastError(configError)
                                continue // Skip to next tunnel
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error preparing config for tunnel $tunnelId", e)
                        broadcastError(VpnError.fromException(e, tunnelId))
                        continue
                    }
                    
                    Log.d(TAG, "VPN config prepared successfully. Auth file: ${preparedConfig.authFile?.absolutePath}")
                    
                    // Create tunnel
                    Log.d(TAG, "Attempting to create tunnel $tunnelId...")
                    val result = connectionManager.createTunnel(
                        tunnelId = tunnelId,
                        ovpnConfig = preparedConfig.ovpnFileContent,
                        authFilePath = preparedConfig.authFile?.absolutePath
                    )
                    
                    if (result.success) {
                        activeTunnels.add(tunnelId)
                        updateActiveConnectionCount()
                        Log.i(TAG, "✅ Successfully created tunnel $tunnelId for VPN config ${vpnConfig.name}")
                    } else {
                        // Broadcast error to UI
                        val error = result.error ?: VpnError(
                            type = VpnError.ErrorType.TUNNEL_ERROR,
                            message = "Failed to create tunnel",
                            tunnelId = tunnelId
                        )
                        Log.e(TAG, "❌ Failed to create tunnel $tunnelId: ${error.message}")
                        Log.e(TAG, "   Error type: ${error.type}, details: ${error.details}")
                        broadcastError(error)
                    }
                }
                
                // Close tunnels that are no longer needed
                val activeTunnelIds = activeVpnConfigIds.map { getTunnelId(it) }.toSet()
                val tunnelsToClose = activeTunnels.filter { it !in activeTunnelIds }
                
                for (tunnelId in tunnelsToClose) {
                    Log.d(TAG, "Closing unused tunnel $tunnelId")
                    connectionManager.closeTunnel(tunnelId)
                    activeTunnels.remove(tunnelId)
                    connectionTracker?.removeRoutesForTunnel(tunnelId)
                    tunnelRoutes.remove(tunnelId)
                    updateActiveConnectionCount()
                    Log.d(TAG, "Closed unused tunnel $tunnelId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error managing tunnels", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Generates a unique tunnel ID from VPN config ID.
     * Format: templateId_regionId (e.g., "nordvpn_UK")
     */
    private suspend fun getTunnelId(vpnConfigId: String): String {
        val vpnConfig = settingsRepository.getVpnConfigById(vpnConfigId)
        return if (vpnConfig != null) {
            "${vpnConfig.templateId}_${vpnConfig.regionId}"
        } else {
            // Fallback if config not found
            "unknown_$vpnConfigId"
        }
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Multi-Region VPN Active")
            .setContentText("Routing traffic based on app rules")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    
    /**
     * Called when a tunnel receives DNS servers from OpenVPN DHCP.
     * This is called from native code via JNI callback when tun_builder_set_dns_options() is invoked.
     */
    private fun onTunnelDnsReceived(tunnelId: String, dnsServers: List<String>) {
        Log.d(TAG, "📥 Tunnel DNS servers received: tunnelId=$tunnelId, dnsServers=$dnsServers")
        
        // Store DNS servers for this tunnel
        val dnsInfo = TunnelDnsServers(tunnelId, dnsServers)
        tunnelDnsServers[tunnelId] = dnsInfo
        
        Log.d(TAG, "✅ Stored DNS servers for tunnel $tunnelId: ${dnsServers.joinToString(", ")}")
        
        // CRITICAL: Check if this is a PRIMARY or SECONDARY tunnel
        // Only re-establish interface for PRIMARY tunnels to avoid closing connections
        val tunnelIp = tunnelIps[tunnelId]
        val isPrimaryTunnel = if (tunnelIp != null) {
            val primaryForSubnet = subnetToPrimaryTunnel[tunnelIp.subnet]
            primaryForSubnet == tunnelId
        } else {
            // If we don't have IP info yet, assume it's primary (will be checked again when IP arrives)
            true
        }
        
        // Schedule interface re-establishment if interface is already established
        // This will update the interface with the correct DNS servers
        if (vpnInterface != null && isPrimaryTunnel) {
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            Log.i(TAG, "📥 VPN interface already established - re-establishing with DNS servers from PRIMARY tunnel")
            Log.i(TAG, "   Tunnel: $tunnelId (PRIMARY)")
            Log.i(TAG, "   DNS servers: ${dnsServers.joinToString(", ")}")
            Log.i(TAG, "   This will update DNS configuration on VPN interface")
            Log.i(TAG, "═══════════════════════════════════════════════════════")
            shouldReestablishInterface = true
            
            // Re-establish interface IMMEDIATELY (don't wait) to ensure DNS servers are available
            // when DNS queries happen. The delay was causing DNS queries to fail because
            // they happened before DNS servers were configured.
            serviceScope.launch {
                // Reduced delay - DNS servers should be available ASAP
                delay(500)  // Small delay to batch multiple DNS server updates
                if (shouldReestablishInterface) {
                    Log.i(TAG, "🔧 Re-establishing VPN interface with DNS servers from tunnel $tunnelId")
                    reestablishInterfaceWithPrimaryIps(packagesWithRules = getCurrentPackagesWithRules())
                    shouldReestablishInterface = false
                    Log.i(TAG, "✅✅✅ VPN interface re-established with DNS servers - DNS should now be working ✅✅✅")
                    
                    // CRITICAL: After re-establishing interface with DNS, ensure TUN reading is active
                    // DNS queries need TUN reading to be active to be routed
                    if (shouldPauseTunReading) {
                        Log.w(TAG, "⚠️  TUN reading still paused after DNS interface re-establishment - forcing resume")
                        shouldPauseTunReading = false
                    }
                }
            }
        } else if (vpnInterface != null && !isPrimaryTunnel) {
            Log.w(TAG, "   Skipping interface re-establishment for SECONDARY tunnel $tunnelId (would break PRIMARY tunnel)")
            Log.w(TAG, "   DNS servers stored but interface not updated (will use PRIMARY tunnel's DNS)")
        } else {
            Log.d(TAG, "VPN interface not yet established - DNS servers will be used when interface is created")
        }
    }

    private fun onTunnelRouteReceived(tunnelId: String, address: String, prefixLength: Int, isIpv6: Boolean) {
        if (isIpv6) {
            Log.w(TAG, "⚠️  Received IPv6 route $address/$prefixLength for $tunnelId - IPv6 routing not yet supported")
            return
        }

        try {
            val inetAddress = InetAddress.getByName(address)
            connectionTracker?.addRouteForTunnel(tunnelId, inetAddress, prefixLength)
            val routes = tunnelRoutes.getOrPut(tunnelId) { mutableListOf() }
            if (routes.none { it.address == address && it.prefixLength == prefixLength && it.isIpv6 == isIpv6 }) {
                routes.add(RouteSubnet(address, prefixLength, isIpv6))
            }
            Log.i(TAG, "   Mapped tunnel route $address/$prefixLength to tunnel $tunnelId")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to process route $address/$prefixLength for $tunnelId: ${e.message}")
        }
    }
    
    /**
     * Called when a tunnel receives its IP address from OpenVPN DHCP.
     * This is called from native code via JNI callback when tun_builder_add_address() is invoked.
     * 
     * CRITICAL: When a tunnel receives its IP, it means the connection is fully established.
     * This is a reliable indicator that we should resume TUN reading (with socket pairs,
     * OpenVPN 3 doesn't read from TUN, so there's no conflict).
     */
    private fun onTunnelIpReceived(tunnelId: String, ip: String, prefixLength: Int) {
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        Log.i(TAG, "📥 Tunnel IP received: tunnelId=$tunnelId, ip=$ip/$prefixLength")
        Log.i(TAG, "   This indicates the connection is fully established!")
        Log.i(TAG, "═══════════════════════════════════════════════════════")
        
        // FALLBACK: Resume TUN reading when tunnel IP is received
        // This is a reliable indicator that connection is complete.
        // With socket pairs, OpenVPN 3 reads from socket pairs, not TUN, so no conflict.
        if (shouldPauseTunReading) {
            Log.i(TAG, "✅✅✅ Resuming TUN reading (tunnel $tunnelId is connected - IP received) ✅✅✅")
            shouldPauseTunReading = false
            
            // Also notify connection state listener to ensure consistency
            // This ensures the callback is called even if polling didn't work
            try {
                val connectionManager = VpnConnectionManager.getInstance()
                val hasConnecting = connectionManager.hasConnectedTunnels().not()
                Log.i(TAG, "   Manually checking connection state: hasConnecting=$hasConnecting")
                Log.i(TAG, "   If hasConnecting=false, connection state listener should resume TUN reading")
            } catch (e: Exception) {
                Log.w(TAG, "   Could not check connection state: ${e.message}")
            }
        } else {
            Log.d(TAG, "   TUN reading already active (shouldPauseTunReading=false)")
        }
        
        try {
            val inetAddress = InetAddress.getByName(ip)
            connectionTracker?.setTunnelForIp(inetAddress, tunnelId)
            Log.i(TAG, "   Mapped tunnel IP ${inetAddress.hostAddress} to tunnel $tunnelId")
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register tunnel IP mapping for $ip on $tunnelId: ${e.message}")
        }
        
        // DOUBLE-CHECK: After a short delay, verify TUN reading is actually active
        // This handles race conditions where the flag might not propagate immediately
        serviceScope.launch {
            delay(2000) // Wait 2 seconds for TUN reading to resume
            if (shouldPauseTunReading) {
                Log.w(TAG, "⚠️  TUN reading still paused after 2 seconds - forcing resume")
                shouldPauseTunReading = false
            } else {
                Log.d(TAG, "✅ Verified: TUN reading is active after tunnel IP received")
            }
        }
        
        // Calculate subnet from IP and prefix length
        val subnet = calculateSubnet(ip, prefixLength)
        
        // Store tunnel IP
        val tunnelIp = TunnelIpAddress(tunnelId, ip, prefixLength, subnet)
        tunnelIps[tunnelId] = tunnelIp
        applyLocalTestRoutesIfNeeded(tunnelId)
        
        // Determine primary tunnel for this subnet (first-come-first-served)
        val primaryTunnel = subnetToPrimaryTunnel.computeIfAbsent(subnet) { tunnelId }
        
        val isPrimaryTunnel = primaryTunnel == tunnelId
        
        if (isPrimaryTunnel) {
            Log.d(TAG, "✅ Tunnel $tunnelId is PRIMARY for subnet $subnet (IP: $ip)")
        } else {
            Log.w(TAG, "⚠️  Tunnel $tunnelId is SECONDARY for subnet $subnet (primary: $primaryTunnel)")
            Log.w(TAG, "   Using primary tunnel's IP on interface, but routing via ConnectionTracker")
            Log.w(TAG, "   ⚠️  IMPORTANT: NOT re-establishing interface to avoid breaking existing tunnels!")
        }
        
        // CRITICAL FIX: Only re-establish interface for PRIMARY tunnels!
        // Re-establishing the interface closes existing connections, which breaks secondary tunnels.
        // Secondary tunnels with the same subnet/IP can still route packets via ConnectionTracker.
        // This is necessary when multiple VPN servers assign the same IP address (e.g., NordVPN).
        if (vpnInterface != null && isPrimaryTunnel) {
            Log.d(TAG, "VPN interface already established - scheduling re-establishment with primary IP")
            shouldReestablishInterface = true
            
            // Re-establish interface in background to avoid blocking
            serviceScope.launch {
                delay(1000)  // Small delay to allow multiple IPs to arrive
                if (shouldReestablishInterface) {
                    reestablishInterfaceWithPrimaryIps(packagesWithRules = getCurrentPackagesWithRules())
                    shouldReestablishInterface = false
                }
            }
        } else if (vpnInterface != null && !isPrimaryTunnel) {
            Log.w(TAG, "   Skipping interface re-establishment for SECONDARY tunnel (would break PRIMARY tunnel)")
            Log.w(TAG, "   Both tunnels will share the interface IP, routing handled by ConnectionTracker")
        } else {
            Log.d(TAG, "VPN interface not yet established - IP will be used when interface is created")
        }
    }
    
    /**
     * Calculate subnet from IP address and prefix length.
     * Returns subnet in format "x.y.z.0/prefixLength" (e.g., "10.100.0.0/16").
     */
    private fun calculateSubnet(ip: String, prefixLength: Int): String {
        val parts = ip.split(".")
        if (parts.size != 4) {
            // Invalid IP format - return as-is
            Log.w(TAG, "Invalid IP format: $ip")
            return "$ip/$prefixLength"
        }
        
        return when (prefixLength) {
            16 -> "${parts[0]}.${parts[1]}.0.0/$prefixLength"
            24 -> "${parts[0]}.${parts[1]}.${parts[2]}.0/$prefixLength"
            8 -> "${parts[0]}.0.0.0/$prefixLength"
            else -> {
                // For non-standard prefixes, use simplified calculation
                // Full implementation would require proper bitmask calculation
                // For now, this works for common cases
                val subnetParts = parts.toMutableList()
                val octetIndex = prefixLength / 8
                if (octetIndex < 4) {
                    for (i in octetIndex until 4) {
                        subnetParts[i] = "0"
                    }
                }
                "${subnetParts.joinToString(".")}/$prefixLength"
            }
        }
    }

    private fun startStatsUpdates() {
        if (statsJob?.isActive == true) return
        connectionStartTime = System.currentTimeMillis()
        statsJob = serviceScope.launch {
            while (isActive) {
                val elapsed = connectionStartTime?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0
                val activeCount = synchronized(activeTunnels) { activeTunnels.size }
                VpnServiceStateTracker.updateStats(
                    VpnStats(
                        bytesSent = 0L,
                        bytesReceived = 0L,
                        connectionTimeSeconds = elapsed,
                        activeConnections = activeCount
                    )
                )
                delay(1000)
            }
        }
    }

    private fun stopStatsUpdates() {
        statsJob?.cancel()
        statsJob = null
        connectionStartTime = null
        VpnServiceStateTracker.updateStats(VpnStats())
    }

    private fun updateActiveConnectionCount() {
        val activeCount = synchronized(activeTunnels) { activeTunnels.size }
        val currentStats = VpnServiceStateTracker.stats.value
        VpnServiceStateTracker.updateStats(
            currentStats.copy(activeConnections = activeCount)
        )
    }
    
    /**
     * Get current list of packages with VPN rules.
     * Used when re-establishing interface to preserve allowed apps.
     */
    private suspend fun getCurrentPackagesWithRules(): List<String> {
        return try {
            // CRITICAL: Use direct DB query, not Flow.first()
            // Flow.first() returns the first emission (which might be stale/empty)
            // Direct query ensures we get CURRENT data from database
            settingsRepository.appRuleDao.getAllRulesList()
                .filter { it.vpnConfigId != null }
                .map { it.packageName }
                .distinct()
        } catch (e: Exception) {
            Log.w(TAG, "Error getting current packages with rules: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Re-establish VPN interface with all primary tunnel IP addresses.
     * This is called when new tunnels receive their IP addresses and the interface
     * needs to be updated to include them.
     */
    private suspend fun reestablishInterfaceWithPrimaryIps(packagesWithRules: List<String>) {
        if (subnetToPrimaryTunnel.isEmpty()) {
            Log.w(TAG, "No tunnel IPs available - cannot re-establish interface")
            return
        }
        
        Log.i(TAG, "🔄 Re-establishing VPN interface with ${subnetToPrimaryTunnel.size} subnet(s)")
        
        // Close current interface
        vpnInterface?.close()
        vpnInterface = null
        vpnOutput?.close()
        vpnOutput = null
        
        // Re-establish with all primary tunnel IPs
        try {
            establishVpnInterface(packagesWithRules)
            
            // Re-initialize packet router with new interface
            if (vpnInterface != null) {
                vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
                initializePacketRouter()
                
                // Restart packet reading
                startTunReader("interface-reestablish")
                
                Log.i(TAG, "✅ VPN interface re-established successfully with ${subnetToPrimaryTunnel.size} subnet(s)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to re-establish VPN interface: ${e.message}", e)
            // Interface will be re-established on next tunnel connection
        }
    }
    
    /**
     * Broadcasts a VPN error to the UI
     */
    private fun broadcastError(error: VpnError) {
        try {
            val intent = android.content.Intent(ACTION_VPN_ERROR).apply {
                putExtra(EXTRA_ERROR_TYPE, error.type.name)
                putExtra(EXTRA_ERROR_MESSAGE, error.message)
                putExtra(EXTRA_ERROR_DETAILS, error.details)
                putExtra(EXTRA_ERROR_TUNNEL_ID, error.tunnelId)
                putExtra(EXTRA_ERROR_TIMESTAMP, error.timestamp)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            Log.d(TAG, "Error broadcast sent: ${error.type} - ${error.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast error", e)
        }
    }
    
    companion object {
        private const val TAG = "VpnEngineService"
        private const val CHANNEL_ID = "vpn_service_channel"
        private const val NOTIFICATION_ID = 1
        private val DEFAULT_FALLBACK_DNS_SERVERS = listOf("8.8.8.8", "8.8.4.4")
        
        const val ACTION_START = "com.multiregionvpn.START_VPN"
        const val ACTION_STOP = "com.multiregionvpn.STOP_VPN"
        const val ACTION_VPN_ERROR = "com.multiregionvpn.VPN_ERROR"
        const val EXTRA_ERROR_TYPE = "error_type"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_ERROR_DETAILS = "error_details"
        const val EXTRA_ERROR_TUNNEL_ID = "error_tunnel_id"
        const val EXTRA_ERROR_TIMESTAMP = "error_timestamp"

        @Volatile
        private var runningInstance: VpnEngineService? = null

        /** Used by HTTP clients to call protect() on sockets. */
        fun getRunningInstance(): VpnEngineService? = runningInstance

        /** For tests and diagnostics. */
        fun isRunning(): Boolean = runningInstance != null

        fun getConnectionTrackerSnapshot(): Map<String, String> {
            return runningInstance?.connectionTracker?.getCurrentPackageMappings() ?: emptyMap()
        }

        fun notifyAppRuleChanged(packageName: String, vpnConfigId: String) {
            runningInstance?.registerPackageForRouting(packageName, vpnConfigId)
        }

        fun notifyAppRuleRemoved(packageName: String) {
            runningInstance?.removePackageRouting(packageName)
        }

        fun notifyAllAppRulesCleared() {
            runningInstance?.clearAllPackageRoutings()
        }
    }
}
