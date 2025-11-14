package com.multiregionvpn

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

/**
 * Data class for the ip-api.com response
 */
@JsonClass(generateAdapter = true)
data class IpInfo(
    @Json(name = "countryCode") val countryCode: String?,
    @Json(name = "query") val ipAddress: String?,
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
    @GET("/json")
    suspend fun getIpInfo(): IpInfo
}

@JsonClass(generateAdapter = true)
data class IpifyResponse(
    @Json(name = "ip") val ip: String?
)

interface IpifyService {
    @GET("/?format=json")
    suspend fun getIp(): IpifyResponse
}

@JsonClass(generateAdapter = true)
data class IfconfigResponse(
    @Json(name = "ip") val ip: String?,
    @Json(name = "country") val country: String?,
    @Json(name = "country_iso") val countryIso: String?,
    @Json(name = "city") val city: String?
)

interface IfconfigService {
    @GET("/json")
    suspend fun getInfo(): IfconfigResponse
}

/**
 * Singleton object to access the IP geolocation API
 */
object IpCheckService {
    private const val DEFAULT_ATTEMPTS = 3
    private const val INITIAL_BACKOFF_MS = 500L

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    // Use ip-api.com with HTTP (requires cleartext traffic enabled)
    // The test manifest has android:usesCleartextTraffic="true"
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://ip-api.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(httpClient)
        .build()

    private val ipifyRetrofit = Retrofit.Builder()
        .baseUrl("https://api.ipify.org/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(httpClient)
        .build()

    private val ifconfigRetrofit = Retrofit.Builder()
        .baseUrl("https://ifconfig.co/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(httpClient)
        .build()

    val api: IpApiService = retrofit.create(IpApiService::class.java)
    private val ipifyApi: IpifyService = ipifyRetrofit.create(IpifyService::class.java)
    private val ifconfigApi: IfconfigService = ifconfigRetrofit.create(IfconfigService::class.java)
    private val cloudflareTraceRequest: Request = Request.Builder()
        .url("https://1.1.1.1/cdn-cgi/trace")
        .header("User-Agent", "multi-region-vpn-test/1.0")
        .build()

    /**
     * Attempts to fetch public IP information using ip-api.com, falling back to api.ipify.org with retries.
     * Throws the last encountered exception if all attempts fail.
     */
    suspend fun getIpInfoWithFallback(
        attempts: Int = DEFAULT_ATTEMPTS,
        fallbackAttempts: Int = DEFAULT_ATTEMPTS
    ): IpInfo {
        var lastError: Throwable? = null

        repeat(attempts) { attempt ->
            println("IpCheckService ▶ Attempting ip-api.com fetch (try ${attempt + 1}/$attempts)")
            val result = runCatching { api.getIpInfo() }
            val ipInfo = result.getOrNull()
            if (ipInfo?.normalizedIpAddress?.isNotBlank() == true) {
                println("IpCheckService ✅ ip-api.com succeeded")
                return ipInfo
            }
            lastError = result.exceptionOrNull() ?: IllegalStateException("Empty IP response from ip-api.com")
            result.exceptionOrNull()?.let { println("IpCheckService ⚠️ ip-api.com attempt ${attempt + 1} failed: ${it.message}") }
            delay(calculateBackoff(attempt))
        }

        repeat(fallbackAttempts) { attempt ->
            println("IpCheckService ▶ Attempting api.ipify.org fetch (try ${attempt + 1}/$fallbackAttempts)")
            val result = runCatching { ipifyApi.getIp() }
            val ipify = result.getOrNull()?.ip
            if (!ipify.isNullOrBlank()) {
                println("IpCheckService ✅ api.ipify.org succeeded")
                return IpInfo(
                    countryCode = null,
                    ipAddress = ipify,
                    country = null,
                    city = null
                )
            }
            lastError = result.exceptionOrNull() ?: IllegalStateException("Empty IP response from api.ipify.org")
            result.exceptionOrNull()?.let { println("IpCheckService ⚠️ api.ipify.org attempt ${attempt + 1} failed: ${it.message}") }
            delay(calculateBackoff(attempt))
        }

        repeat(fallbackAttempts) { attempt ->
            println("IpCheckService ▶ Attempting ifconfig.co fetch (try ${attempt + 1}/$fallbackAttempts)")
            val result = runCatching { ifconfigApi.getInfo() }
            val response = result.getOrNull()
            val ip = response?.ip
            if (!ip.isNullOrBlank()) {
                println("IpCheckService ✅ ifconfig.co succeeded")
                return IpInfo(
                    countryCode = response.countryIso,
                    ipAddress = ip,
                    country = response.country,
                    city = response.city
                )
            }
            lastError = result.exceptionOrNull() ?: IllegalStateException("Empty IP response from ifconfig.co")
            result.exceptionOrNull()?.let { println("IpCheckService ⚠️ ifconfig.co attempt ${attempt + 1} failed: ${it.message}") }
            delay(calculateBackoff(attempt))
        }

        println("IpCheckService ▶ Attempting Cloudflare trace fallback")
        val cloudflareIp = runCatching { fetchIpFromCloudflareTrace() }.getOrNull()
        if (!cloudflareIp.isNullOrBlank()) {
            println("IpCheckService ✅ Cloudflare trace succeeded with IP $cloudflareIp")
            return IpInfo(
                countryCode = null,
                ipAddress = cloudflareIp,
                country = null,
                city = null
            )
        }

        throw lastError ?: IllegalStateException("Unable to fetch public IP information")
    }

    private suspend fun fetchIpFromCloudflareTrace(): String? = withContext(Dispatchers.IO) {
        val response = runCatching { httpClient.newCall(cloudflareTraceRequest).execute() }
            .onFailure { println("IpCheckService ⚠️ Cloudflare trace request failed: ${it.message}") }
            .getOrNull() ?: return@withContext null
        response.use { resp ->
            if (!resp.isSuccessful) {
                println("IpCheckService ⚠️ Cloudflare trace returned HTTP ${resp.code}")
                return@withContext null
            }
            val body = resp.body?.string() ?: return@withContext null
            val ipLine = body.lineSequence().firstOrNull { it.startsWith("ip=") } ?: return@withContext null
            return@withContext ipLine.removePrefix("ip=").trim().takeIf { it.isNotEmpty() }
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        val multiplier = (attempt + 1).coerceAtMost(4)
        return INITIAL_BACKOFF_MS * multiplier
    }
}

