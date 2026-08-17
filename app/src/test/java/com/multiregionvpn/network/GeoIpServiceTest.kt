package com.multiregionvpn.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit test for GeoIpResponse JSON parsing from freeipapi.com
 */
class GeoIpServiceTest {

    @Test
    fun testGeoIpResponseDeserialization() {
        val json = """
            {
                "ipVersion": 4,
                "ipAddress": "34.46.237.233",
                "countryName": "United States",
                "countryCode": "US",
                "regionName": "Iowa"
            }
        """.trimIndent()

        val gson = Gson()
        val response = gson.fromJson(json, GeoIpResponse::class.java)

        assertNotNull(response)
        assertEquals("US", response.countryCode)
        assertEquals("United States", response.country)
        assertEquals("Iowa", response.region)
    }
}
