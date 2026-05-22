package com.multiregionvpn.network

import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeoIpServiceTest {

    private lateinit var mockApi: GeoIpApi
    private lateinit var service: GeoIpService

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockApi = mockk()
        service = GeoIpService(mockApi)
    }

    @Test
    fun `getCurrentRegion returns country code on success`() = runBlocking {
        val response = GeoIpResponse(countryCode = "US", country = "United States", region = "California")
        coEvery { mockApi.getCurrentLocation() } returns response

        val result = service.getCurrentRegion()
        assertEquals("US", result)
    }

    @Test
    fun `getCurrentRegion returns null on exception`() = runBlocking {
        coEvery { mockApi.getCurrentLocation() } throws RuntimeException("Network error")

        val result = service.getCurrentRegion()
        assertNull(result)
    }
}
