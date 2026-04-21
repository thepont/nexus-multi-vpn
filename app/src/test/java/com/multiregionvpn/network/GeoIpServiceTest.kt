package com.multiregionvpn.network

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoIpServiceTest {

    @Test
    fun `getCurrentRegion returns countryCode from API`() = runTest {
        // GIVEN
        val mockApi = mockk<GeoIpApi>()
        val response = GeoIpResponse(countryCode = "US", country = "United States", region = "Iowa")
        coEvery { mockApi.getCurrentLocation() } returns response

        val service = GeoIpService(mockApi)

        // WHEN
        val result = service.getCurrentRegion()

        // THEN
        assertEquals("US", result)
    }

    @Test
    fun `getCurrentRegion returns null on API error`() = runTest {
        // GIVEN
        val mockApi = mockk<GeoIpApi>()
        coEvery { mockApi.getCurrentLocation() } throws Exception("API error")

        val service = GeoIpService(mockApi)

        // WHEN
        val result = service.getCurrentRegion()

        // THEN
        assertEquals(null, result)
    }
}
