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
import kotlinx.coroutines.delay
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
    private var connectionTracker: ConnectionTracker? = null
    private var vpnOutput: FileOutputStream? = null
    private val activeTunnels = mutableSetOf<String>() // Track tunnel IDs to avoid duplicates
    
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
    
    private val tunnelIps = mutableMapOf<String, TunnelIpAddress>()  // tunnelId -> IP address
    private val tunnelDnsServers = mutableMapOf<String, TunnelDnsServers>()  // tunnelId -> DNS servers
    private val subnetToPrimaryTunnel = mutableMapOf<String, String>()  // subnet -> primary tunnelId
    private var shouldReestablishInterface = false  // Flag to trigger interface re-establishment
    private var currentAllowedPackages = emptySet<String>()  // Track current allowed apps for split tunneling
    
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
        connectionTracker = ConnectionTracker(this, packageManager)
        
        // CRITICAL: Register all packages with app rules so ConnectionTracker knows about them
        // In Global VPN mode, we don't use addAllowedApplication(), so ConnectionTracker
        // needs to be explicitly told which packages to track for routing
        serviceScope.launch {
            try {
                val appRules = settingsRepository.getAllAppRules().first()
                Log.i(TAG, "📝 Registering ${appRules.size} packages with ConnectionTracker for VPN routing")
                appRules.forEach { appRule ->
                    if (appRule.vpnConfigId != null) {
                        // Register package with ConnectionTracker so it knows to track this app
                        val vpnConfig = settingsRepository.getVpnConfigById(appRule.vpnConfigId!!)
                        if (vpnConfig != null) {
                            val tunnelId = "${vpnConfig.templateId}_${vpnConfig.regionId}"
                            val registered = connectionTracker?.setPackageToTunnel(appRule.packageName, tunnelId)
                            if (registered == true) {
                                Log.d(TAG, "   ✅ Registered ${appRule.packageName} → tunnel $tunnelId")
                            } else {
                                Log.w(TAG, "   ⚠️  Failed to register ${appRule.packageName} (not installed?)")
                            }
                        }
                    }
                }
                Log.i(TAG, "✅ Package registration complete - ConnectionTracker ready for routing")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to register packages with ConnectionTracker", e)
            }
        }
        
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
            // CRITICAL: Use direct database query (not Flow.first()) to ensure we get
            // committed data, not stale Flow emission
            val appRules = kotlinx.coroutines.runBlocking {
                settingsRepository.appRuleDao.getAllRulesList()
            }
            Log.d(TAG, "App rules found: ${appRules.size}, packages: ${appRules.map { it.packageName }}")
            
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
            for (vpnConfigId in activeVpnConfigIds) {
                try {
                    val vpnConfig = kotlinx.coroutines.runBlocking {
                        settingsRepository.getVpnConfigById(vpnConfigId)
                    }
                    if (vpnConfig != null) {
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
        
        // SPLIT TUNNELING: Only apps with VPN rules use the VPN
        // Apps without rules bypass VPN entirely (use normal internet)
        //
        // THE "GLOBAL VPN" APPROACH WAS BROKEN:
        // - sendToDirectInternet() writes packets back to TUN → creates loop
        // - No way to truly bypass VPN once traffic enters TUN interface
        // - Result: Apps without rules fail (DNS errors, no connectivity)
        //
        // CORRECT APPROACH: addAllowedApplication()
        // - ONLY apps with VPN rules have traffic routed through VPN
        // - Apps without rules never enter VPN (use normal Android routing)
        // - Multi-tunnel routing STILL WORKS (PacketRouter routes VPN apps to correct tunnel)
        // - Apps without rules get normal internet access
        packagesWithRules.forEach { packageName ->
            try {
                builder.addAllowedApplication(packageName)
                Log.d(TAG, "   ✅ VPN allowed: $packageName")
            } catch (e: Exception) {
                Log.w(TAG, "   ⚠️  Could not allow $packageName: ${e.message}")
            }
        }
        
        // CRITICAL: Do NOT disallow our own package!
        // E2E tests (com.multiregionvpn.test) need VPN access
        Log.d(TAG, "✅ VPN service NOT disallowed - E2E tests need access")
        
        Log.i(TAG, "✅ Split tunneling: ${packagesWithRules.size} app(s) use VPN")
        Log.i(TAG, "   Apps WITHOUT rules use direct internet (bypass VPN entirely)")
        
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
        val dnsServersToUse = mutableSetOf<String>()
        
        // Collect DNS servers from all tunnels (not just primary) - we want all available DNS servers
        // This ensures DNS resolution works even if some tunnels haven't connected yet
        tunnelDnsServers.values.forEach { tunnelDns ->
            tunnelDns.dnsServers.forEach { dnsServer ->
                dnsServersToUse.add(dnsServer)
            }
            Log.d(TAG, "✅ Added DNS servers from tunnel ${tunnelDns.tunnelId}: ${tunnelDns.dnsServers.joinToString(", ")}")
        }
        
        // CRITICAL: Android's VpnService requires DNS servers to be set for DNS queries
        // to go through the VPN interface. If no DNS servers are set, Android will use
        // system DNS which bypasses the VPN interface entirely.
        if (dnsServersToUse.isNotEmpty()) {
            dnsServersToUse.forEach { dnsServer ->
                builder.addDnsServer(dnsServer)
                Log.d(TAG, "   ➕ Added DNS server: $dnsServer")
            }
            Log.i(TAG, "✅✅✅ DNS servers configured for VPN interface: ${dnsServersToUse.joinToString(", ")} (from OpenVPN DHCP)")
            Log.i(TAG, "   This ensures DNS queries from allowed apps go through VPN interface")
        } else {
            // CRITICAL: Without DNS servers, Android will NOT route DNS queries through VPN
            // This causes DNS queries to bypass VPN entirely, even if route 0.0.0.0/0 is set.
            // 
            // THE FUNKY ISSUE: If VPN interface is established without DNS servers, Android
            // might cache the decision to bypass VPN for DNS, and even after re-establishment
            // with DNS servers, DNS queries might still bypass VPN.
            //
            // SOLUTION: Use a temporary fallback DNS server (8.8.8.8) when interface is first
            // established. This ensures DNS queries go through VPN interface from the start.
            // The interface will be re-established with proper VPN DNS servers when available.
            val fallbackDns = "8.8.8.8"  // Google DNS as fallback
            builder.addDnsServer(fallbackDns)
            Log.w(TAG, "⚠️  No DNS servers available yet - using fallback DNS server: $fallbackDns")
            Log.w(TAG, "   This ensures DNS queries go through VPN interface from the start")
            Log.w(TAG, "   VPN DNS servers will replace this when interface is re-established")
            Log.i(TAG, "   ✅ Added fallback DNS server: $fallbackDns")
        }
        
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
        Log.d(TAG, "Starting to collect app rules...")
        settingsRepository.getAllAppRules().collect { appRules ->
            Log.d(TAG, "App rules collected: ${appRules.size} rules found")
            
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
                            serviceScope.launch { readPacketsFromTun() }
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
        runningInstance = null  // Clear static reference for socket protection
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
    
    /**
     * Get current list of packages with VPN rules.
     * Used when re-establishing interface to preserve allowed apps.
     */
    private suspend fun getCurrentPackagesWithRules(): List<String> {
        return try {
            settingsRepository.getAllAppRules()
                .first()
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
                serviceScope.launch {
                    readPacketsFromTun()
                }
                
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
        
        const val ACTION_START = "com.multiregionvpn.START_VPN"
        const val ACTION_STOP = "com.multiregionvpn.STOP_VPN"
        const val ACTION_VPN_ERROR = "com.multiregionvpn.VPN_ERROR"
        const val EXTRA_ERROR_TYPE = "error_type"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_ERROR_DETAILS = "error_details"
        const val EXTRA_ERROR_TUNNEL_ID = "error_tunnel_id"
        const val EXTRA_ERROR_TIMESTAMP = "error_timestamp"
        
        // Static reference to running instance for socket protection
        @Volatile
        private var runningInstance: VpnEngineService? = null
        
        /**
         * Get the running VPN service instance.
         * Used by HTTP clients to call protect() on sockets.
         * Returns null if service is not running.
         */
        fun getRunningInstance(): VpnEngineService? = runningInstance
    }
}
