package com.multiregionvpn.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class GeoIpServiceTest {

    private val api: GeoIpApi = mockk()
    private val service = GeoIpService(api, Dispatchers.Unconfined)

    @Test
    fun `getCurrentRegion returns countryCode on success`() = runTest {
        val response = GeoIpResponse(
            countryCode = "UK",
            country = "United Kingdom",
            region = "London"
        )
        coEvery { api.getCurrentLocation() } returns response

        val result = service.getCurrentRegion()
        assertEquals("UK", result)
    }

    @Test
    fun `getCurrentRegion returns null on api error`() = runTest {
        coEvery { api.getCurrentLocation() } throws Exception("Network error")

        val result = service.getCurrentRegion()
        assertNull(result)
    }
}
