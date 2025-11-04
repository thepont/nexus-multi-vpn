package com.multiregionvpn.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.multiregionvpn.ui.MainActivity
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Foreground Service that extends VpnService.
 * Routes all device traffic and manages packet routing.
 */
@AndroidEntryPoint
class VpnEngineService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var vpnTemplateService: VpnTemplateService
    
    private lateinit var packetRouter: PacketRouter
    private var vpnOutput: FileOutputStream? = null
    private val activeTunnels = mutableSetOf<String>() // Track tunnel IDs to avoid duplicates
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Initialize VpnConnectionManager early so it's available throughout service lifetime
        // This ensures getInstance() calls don't fail
        try {
            VpnConnectionManager.initialize(this, this)
            Log.d(TAG, "VpnConnectionManager initialized in onCreate()")
        } catch (e: Exception) {
            Log.w(TAG, "VpnConnectionManager already initialized or error: ${e.message}")
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
                    connectionManager.setTunFileDescriptor(duplicatedFd)
                    Log.d(TAG, "TUN file descriptor duplicated and set in VpnConnectionManager: $duplicatedFd")
                    Log.d(TAG, "   Original PFD remains valid for VpnEngineService packet reading")
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
                vpnOutput?.write(packet)
                vpnOutput?.flush()
                Log.v(TAG, "Received ${packet.size} bytes from tunnel $tunnelId, wrote to TUN")
            } catch (e: Exception) {
                Log.e(TAG, "Error writing packet from tunnel $tunnelId to TUN", e)
            }
        }
        
        packetRouter = PacketRouter(
            this,
            settingsRepository,
            this,
            connectionManager,
            vpnOutput
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
        
        if (vpnInterface != null) {
            Log.w(TAG, "VPN already started")
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
            val appRules = kotlinx.coroutines.runBlocking {
                settingsRepository.getAllAppRules().first()
            }
            
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
                    manageTunnels()
                }
                Log.i(TAG, "✅ VPN service started (interface will be established when rules are added)")
                return
            }
            
            // Establish VPN interface with split tunneling (only for apps with rules)
            establishVpnInterface(packagesWithRules)
            
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
            serviceScope.launch {
                readPacketsFromTun()
            }
            
            // Start tunnel management - monitors app rules and creates tunnels
            serviceScope.launch {
                manageTunnels()
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
        if (packagesWithRules.isEmpty()) {
            Log.w(TAG, "⚠️  No app rules found - NOT establishing VPN interface")
            Log.w(TAG, "   This is correct split tunneling behavior: no apps = no VPN interface = no VPN traffic")
            return
        }
        
        if (vpnInterface != null) {
            Log.d(TAG, "VPN interface already established")
            return
        }
        
        Log.d(TAG, "Creating VPN interface builder...")
        Log.d(TAG, "Packages with VPN rules: $packagesWithRules")
        
        val builder = Builder()
        builder.setSession("MultiRegionVPN")
        builder.addAddress("10.0.0.2", 30)
        
        // Allow only specific apps to use VPN (split tunneling)
        packagesWithRules.forEach { packageName ->
            try {
                builder.addAllowedApplication(packageName)
                Log.d(TAG, "Added allowed application for split tunneling: $packageName")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add allowed application $packageName: ${e.message}")
            }
        }
        
        // Add routes for VPN traffic (only applies to allowed apps)
        builder.addRoute("0.0.0.0", 0) // Route all traffic for allowed apps only
        
        // Set DNS servers for VPN (only used by allowed apps)
        builder.addDnsServer("8.8.8.8")
        Log.d(TAG, "DNS server 8.8.8.8 configured for VPN")
        
        builder.setMtu(1500)
        
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
                return
            }
            Log.i(TAG, "✅ VPN interface established successfully")
            Log.d(TAG, "VPN interface ParcelFileDescriptor: ${if (vpnInterface != null) "valid" else "null"}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception while establishing VPN interface: ${e.message}")
            Log.e(TAG, "Stack trace:", e)
            return
        }
        
        Log.i(TAG, "✅ VPN interface established with split tunneling")
    }
    
    private fun stopVpn() {
        try {
            // Close all active tunnels (only if VpnConnectionManager was initialized)
            serviceScope.launch {
                try {
                    VpnConnectionManager.getInstance().closeAll()
                } catch (e: IllegalStateException) {
                    // VpnConnectionManager not initialized yet - that's okay
                    Log.v(TAG, "VpnConnectionManager not initialized, skipping cleanup")
                }
                activeTunnels.clear()
            }
            
            vpnOutput?.close()
            vpnOutput = null
            vpnInterface?.close()
            vpnInterface = null
            stopForeground(true)
            stopSelf()
            Log.d(TAG, "VPN stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN", e)
        }
    }
    
    /**
     * Reads packets from TUN interface and routes them based on app rules.
     * 
     * CRITICAL ARCHITECTURE DECISION:
     * We MUST keep reading from TUN to support multi-tunnel routing (different apps → different VPNs).
     * 
     * If we stop reading when OpenVPN 3 connects, OpenVPN 3 will take over TUN FD completely
     * and route ALL packets through ONE connection, breaking multi-tunnel routing.
     * 
     * Trade-off: This creates a race condition risk where both VpnEngineService and OpenVPN 3
     * read from the same TUN FD. However, this is necessary for the multi-tunnel architecture.
     * 
     * The race condition is mitigated by:
     * 1. VpnEngineService reads packets first (gets priority)
     * 2. Packets are routed based on app rules before OpenVPN 3 sees them
     * 3. OpenVPN 3 receives packets via sendPacket() (not direct TUN read)
     * 
     * NOTE: OpenVPN 3's connect() method may also try to read from TUN, but we prioritize
     * our routing logic. This is a known limitation of using OpenVPN 3 ClientAPI with
     * custom routing requirements.
     */
    private suspend fun readPacketsFromTun() {
        val vpnInput = vpnInterface?.let { FileInputStream(it.fileDescriptor) }
            ?: run {
                Log.w(TAG, "Cannot read packets - VPN interface not established")
                return
            }
        
        val buffer = ByteArray(32767)
        
        Log.i(TAG, "📖 Starting TUN packet reading loop")
        Log.i(TAG, "   Will route packets based on app rules to support multi-tunnel routing")
        Log.i(TAG, "   NOTE: This may conflict with OpenVPN 3's direct TUN reading, but is required for multi-tunnel support")
        
        while (vpnInterface != null && serviceScope.isActive) {
            try {
                val length = vpnInput.read(buffer)
                if (length > 0) {
                    val packet = buffer.copyOf(length)
                    // Pass packet to PacketRouter for routing based on app rules
                    // This enables multi-tunnel routing (different apps → different VPNs)
                    packetRouter.routePacket(packet)
                }
            } catch (e: Exception) {
                if (vpnInterface != null) {
                    Log.e(TAG, "Error reading packet from TUN", e)
                }
                break
            }
        }
        Log.d(TAG, "readPacketsFromTun() coroutine stopped")
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
     */
    private suspend fun manageTunnels() {
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
        Log.d(TAG, "Starting to collect app rules...")
        settingsRepository.getAllAppRules().collect { appRules ->
            Log.d(TAG, "App rules collected: ${appRules.size} rules found")
            
            // Get packages with VPN rules
            val packagesWithRules = appRules
                .filter { it.vpnConfigId != null }
                .map { it.packageName }
                .distinct()
            
            // If VPN interface is not established but we have rules, establish it now
            if (vpnInterface == null && packagesWithRules.isNotEmpty()) {
                Log.i(TAG, "App rules detected - establishing VPN interface for split tunneling")
                try {
                    establishVpnInterface(packagesWithRules)
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
                    // Re-initialize packet router (will handle null interface)
                    initializePacketRouter()
                    Log.i(TAG, "✅ VPN interface closed (no apps need VPN routing)")
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing VPN interface", e)
                }
                return@collect
            }
            
            // If VPN interface is not established but we have rules, establish it now
            if (vpnInterface == null && packagesWithRules.isNotEmpty()) {
                Log.i(TAG, "App rules detected - establishing VPN interface for split tunneling")
                try {
                    establishVpnInterface(packagesWithRules)
                    if (vpnInterface != null) {
                        // Interface was established - set up streams and router
                        vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
                        initializePacketRouter()
                        // Start reading packets now that interface is established
                        serviceScope.launch {
                            readPacketsFromTun()
                        }
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
                    
                    // Prepare config using VpnTemplateService
                    try {
                        Log.d(TAG, "Preparing VPN config for tunnel $tunnelId...")
                        val preparedConfig = try {
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
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Unexpected error preparing/configuring tunnel $tunnelId", e)
                        e.printStackTrace()
                        // Broadcast unexpected errors too
                        val error = VpnError.fromException(e, tunnelId)
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
    
    override fun onDestroy() {
        super.onDestroy()
        vpnOutput?.close()
        vpnInterface?.close()
        serviceScope.launch {
            try {
                VpnConnectionManager.getInstance().closeAll()
            } catch (e: IllegalStateException) {
                // VpnConnectionManager not initialized yet - that's okay
                Log.v(TAG, "VpnConnectionManager not initialized in onDestroy, skipping cleanup")
            }
        }
        serviceScope.cancel()
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
        
        const val ACTION_START = "com.multiregionvpn.START_VPN"
        const val ACTION_STOP = "com.multiregionvpn.STOP_VPN"
        const val ACTION_VPN_ERROR = "com.multiregionvpn.VPN_ERROR"
        const val EXTRA_ERROR_TYPE = "error_type"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_ERROR_DETAILS = "error_details"
        const val EXTRA_ERROR_TUNNEL_ID = "error_tunnel_id"
        const val EXTRA_ERROR_TIMESTAMP = "error_timestamp"
    }
}
