package com.multiregionvpn.di

import android.content.Context
import com.multiregionvpn.data.database.AppDatabase
import com.multiregionvpn.data.database.AppRuleDao
import com.multiregionvpn.data.database.ProviderCredentialsDao
import com.multiregionvpn.data.database.VpnConfigDao
import com.multiregionvpn.data.repository.SettingsRepository
import com.multiregionvpn.network.NordVpnApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return androidx.room.Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "region_router_db"
        )
            .addCallback(AppDatabase.PresetRuleCallback())
            .build()
    }

    // --- Provide all our DAOs ---
    @Provides
    @Singleton
    fun provideVpnConfigDao(db: AppDatabase): VpnConfigDao = db.vpnConfigDao()

    @Provides
    @Singleton
    fun provideAppRuleDao(db: AppDatabase): AppRuleDao = db.appRuleDao()

    @Provides
    @Singleton
    fun provideProviderCredentialsDao(db: AppDatabase): ProviderCredentialsDao = db.providerCredentialsDao()

    @Provides
    @Singleton
    fun providePresetRuleDao(db: AppDatabase): com.multiregionvpn.data.database.PresetRuleDao = db.presetRuleDao()
    
    // --- Provide our Repository ---
    @Provides
    @Singleton
    fun provideSettingsRepository(
        vpnConfigDao: VpnConfigDao,
        appRuleDao: AppRuleDao,
        credsDao: ProviderCredentialsDao,
        presetRuleDao: com.multiregionvpn.data.database.PresetRuleDao
    ): SettingsRepository {
        return SettingsRepository(vpnConfigDao, appRuleDao, credsDao, presetRuleDao)
    }
    
    // --- Provide Retrofit for NordVPN API ---
    // This HTTP client must bypass the VPN to download configs
    @Provides
    @Singleton
    fun provideNordVpnApiService(@ApplicationContext context: Context): NordVpnApiService {
        // Create OkHttpClient that bypasses VPN using socket factory
        // This is critical: config downloads must work even when VPN is active
        val socketFactory = object : javax.net.SocketFactory() {
            override fun createSocket(): java.net.Socket {
                val socket = java.net.Socket()
                // Try to protect socket if VPN service is running
                try {
                    val vpnService = com.multiregionvpn.core.VpnEngineService.getRunningInstance()
                    if (vpnService != null && vpnService.protect(socket)) {
                        android.util.Log.d("OkHttp", "Socket protected from VPN routing")
                    }
                } catch (e: Exception) {
                    // VPN not running or can't protect - that's fine
                    android.util.Log.v("OkHttp", "Could not protect socket: ${e.message}")
                }
                return socket
            }
            
            override fun createSocket(host: String, port: Int) = createSocket().also {
                it.connect(java.net.InetSocketAddress(host, port))
            }
            
            override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int) =
                createSocket().also {
                    it.bind(java.net.InetSocketAddress(localHost, localPort))
                    it.connect(java.net.InetSocketAddress(host, port))
                }
            
            override fun createSocket(host: java.net.InetAddress, port: Int) = createSocket().also {
                it.connect(java.net.InetSocketAddress(host, port))
            }
            
            override fun createSocket(address: java.net.InetAddress, port: Int, localAddress: java.net.InetAddress, localPort: Int) =
                createSocket().also {
                    it.bind(java.net.InetSocketAddress(localAddress, localPort))
                    it.connect(java.net.InetSocketAddress(address, port))
                }
        }
        
        val client = OkHttpClient.Builder()
            .socketFactory(socketFactory)
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://downloads.nordcdn.com/")
            .client(client)
            .build()
        
        return retrofit.create(NordVpnApiService::class.java)
    }
}
