package com.multiregionvpn.network

import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

/**
 * Geolocation response from freeipapi.com
 */
data class GeoIpResponse(
    val countryCode: String?,
    @SerializedName("countryName") val country: String?,
    @SerializedName("regionName") val region: String?
)

interface GeoIpApi {
    @GET("json")
    suspend fun getCurrentLocation(): GeoIpResponse
}

/**
 * Service to get current geographic region securely using HTTPS.
 * Hardened to use free.freeipapi.com to prevent unencrypted HTTP traffic leaks.
 */
class GeoIpService {
    private val api = Retrofit.Builder()
        .baseUrl("https://free.freeipapi.com/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GeoIpApi::class.java)
    
    suspend fun getCurrentRegion(): String? = withContext(Dispatchers.IO) {
        try {
            val response = api.getCurrentLocation()
            val region = response.countryCode
            Log.d(TAG, "Current region detected: $region")
            region
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current region", e)
            null
        }
    }
    
    companion object {
        private const val TAG = "GeoIpService"
    }
}
