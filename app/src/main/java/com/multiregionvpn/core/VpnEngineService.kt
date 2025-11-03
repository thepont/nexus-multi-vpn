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
import java.io.FileInputStream
import java.io.FileOutputStream

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
    }
    
    private fun initializePacketRouter() {
        // Initialize VpnConnectionManager with Context and VpnService for real OpenVPN clients
        val connectionManager = VpnConnectionManager.initialize(this, this)
        
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
            startForeground(NOTIFICATION_ID, createNotification())
            Log.d(TAG, "✅ Foreground notification started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground notification", e)
            // Continue anyway - might be a race condition
        }
        
        try {
            Log.d(TAG, "Creating VPN interface builder...")
            val builder = Builder()
            builder.setSession("MultiRegionVPN")
            builder.addAddress("10.0.0.2", 30)
            builder.addRoute("0.0.0.0", 0) // Route all traffic
            builder.addDnsServer("8.8.8.8")
            builder.setMtu(1500)
            
            Log.d(TAG, "Establishing VPN interface...")
            Log.d(TAG, "NOTE: If this fails, VPN permission may not be granted")
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "❌ Failed to establish VPN interface")
                Log.e(TAG, "This usually means:")
                Log.e(TAG, "  1. VPN permission was not granted")
                Log.e(TAG, "  2. Another VPN is already active")
                Log.e(TAG, "  3. System resources unavailable")
                stopForeground(true)
                stopSelf()
                return
            }
            
            Log.i(TAG, "✅ VPN interface established")
            
            // Create output stream for writing packets back to TUN interface
            vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
            Log.d(TAG, "VPN output stream created")
            
            // Initialize packet router with the output stream
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
            
            // Start packet processing loops
            serviceScope.launch {
                startOutboundLoop()
            }
            serviceScope.launch {
                startInboundLoop()
            }
            
            // Start tunnel management - monitors app rules and creates tunnels
            serviceScope.launch {
                manageTunnels()
            }
            
            Log.d(TAG, "VPN started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopSelf()
        }
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
    
    private suspend fun startOutboundLoop() {
        val vpnInput = vpnInterface?.let { FileInputStream(it.fileDescriptor) }
        val buffer = ByteArray(32767)
        
        while (vpnInterface != null && serviceScope.isActive) {
            try {
                val length = vpnInput?.read(buffer) ?: break
                if (length > 0) {
                    val packet = buffer.copyOf(length)
                    // Pass packet to PacketRouter
                    packetRouter.routePacket(packet)
                }
            } catch (e: Exception) {
                if (vpnInterface != null) {
                    Log.e(TAG, "Error in outbound loop", e)
                }
                break
            }
        }
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
            
            if (vpnInterface == null) {
                // VPN not running, skip tunnel management
                Log.w(TAG, "VPN interface not available, skipping tunnel management")
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
                        val preparedConfig = vpnTemplateService.prepareConfig(vpnConfig)
                        Log.d(TAG, "VPN config prepared successfully. Auth file: ${preparedConfig.authFile?.absolutePath}")
                        
                        // Create tunnel
                        Log.d(TAG, "Attempting to create tunnel $tunnelId...")
                        val created = connectionManager.createTunnel(
                            tunnelId = tunnelId,
                            ovpnConfig = preparedConfig.ovpnFileContent,
                            authFilePath = preparedConfig.authFile?.absolutePath
                        )
                        
                        if (created) {
                            activeTunnels.add(tunnelId)
                            Log.i(TAG, "✅ Successfully created tunnel $tunnelId for VPN config ${vpnConfig.name}")
                        } else {
                            Log.e(TAG, "❌ Failed to create tunnel $tunnelId - createTunnel() returned false")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error preparing/configuring tunnel $tunnelId", e)
                        e.printStackTrace()
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
    
    companion object {
        private const val TAG = "VpnEngineService"
        private const val CHANNEL_ID = "vpn_service_channel"
        private const val NOTIFICATION_ID = 1
        
        const val ACTION_START = "com.multiregionvpn.START_VPN"
        const val ACTION_STOP = "com.multiregionvpn.STOP_VPN"
    }
}
