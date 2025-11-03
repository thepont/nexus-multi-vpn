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
        return AppDatabase.getDatabase(context)
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
    @Provides
    @Singleton
    fun provideNordVpnApiService(): NordVpnApiService {
        val client = OkHttpClient.Builder()
            .build()
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://downloads.nordcdn.com/")
            .client(client)
            .build()
        
        return retrofit.create(NordVpnApiService::class.java)
    }
}
