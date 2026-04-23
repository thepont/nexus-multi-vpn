package com.multiregionvpn

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

/**
 * Data class for the ip-api.com response
 */
@JsonClass(generateAdapter = true)
data class IpInfo(
    @Json(name = "country_code") val countryCode: String?,
    @Json(name = "ip") val ipAddress: String?,
    @Json(name = "country") val country: String?,
    @Json(name = "city") val city: String?
) {
    val normalizedCountryCode: String?
        get() = countryCode
    
    val normalizedIpAddress: String?
        get() = ipAddress
}

/**
 * Retrofit interface for IP geolocation checking
 */
interface IpApiService {
    @GET(".")
    suspend fun getIpInfo(): IpInfo
}

/**
 * Singleton object to access the IP geolocation API
 */
object IpCheckService {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Use ipwho.is with HTTPS (more reliable and secure)
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://ipwho.is/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: IpApiService = retrofit.create(IpApiService::class.java)
}

