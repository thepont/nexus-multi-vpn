package com.multiregionvpn.network

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before

class GeoIpServiceTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @Test
    fun `getCurrentRegion returns country code on success`() = runBlocking {
        // GIVEN: A successful response from ipwho.is
        val mockApi = mockk<GeoIpApi>()
        val mockResponse = GeoIpResponse(
            countryCode = "AU",
            country = "Australia",
            region = "Victoria"
        )
        coEvery { mockApi.getCurrentLocation() } returns mockResponse

        // Use reflection to set the private api field
        val service = GeoIpService()
        val apiField = GeoIpService::class.java.getDeclaredField("api")
        apiField.isAccessible = true
        apiField.set(service, mockApi)

        // WHEN: Getting current region
        val region = service.getCurrentRegion()

        // THEN: It should return the country code
        assertEquals("AU", region)
    }

    @Test
    fun `getCurrentRegion returns null on error`() = runBlocking {
        // GIVEN: An error from the API
        val mockApi = mockk<GeoIpApi>()
        coEvery { mockApi.getCurrentLocation() } throws Exception("Network error")

        val service = GeoIpService()
        val apiField = GeoIpService::class.java.getDeclaredField("api")
        apiField.isAccessible = true
        apiField.set(service, mockApi)

        // WHEN: Getting current region
        val region = service.getCurrentRegion()

        // THEN: It should return null
        assertNull(region)
    }
}
