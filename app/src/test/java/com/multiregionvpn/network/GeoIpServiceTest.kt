package com.multiregionvpn.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GeoIpServiceTest {

    @Test
    fun testGeoIpResponseDeserialization() {
        val json = """
            {
                "countryCode": "US",
                "countryName": "United States",
                "regionName": "Iowa",
                "cityName": "Council Bluffs"
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
