package com.multiregionvpn

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

// SECURITY: Migrated from insecure ip-api.com to secure freeipapi.com HTTPS endpoint.
// Moshi annotations map camelCase fields for backwards compatibility with tests.
@JsonClass(generateAdapter = true)
data class IpInfo(
    @Json(name = "countryCode") val countryCode: String?,
    @Json(name = "ipAddress") val ipAddress: String?,
    @Json(name = "countryName") val country: String?,
    @Json(name = "cityName") val city: String?
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
    // SECURITY: Path must be relative (no leading slash) so it doesn't strip the base path '/api/'
    @GET("json")
    suspend fun getIpInfo(): IpInfo
}

/**
 * Singleton object to access the IP geolocation API
 */
object IpCheckService {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // SECURITY: Use freeipapi.com with HTTPS for secure, encrypted transport
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://free.freeipapi.com/api/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: IpApiService = retrofit.create(IpApiService::class.java)
}

