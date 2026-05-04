package com.multiregionvpn.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoIpServiceTest {

    @Test
    fun testGeoIpResponseDeserialization() {
        val json = """
            {
                "ip": "8.8.4.4",
                "success": true,
                "country": "United States",
                "country_code": "US",
                "region": "California"
            }
        """.trimIndent()

        val gson = Gson()
        val response = gson.fromJson(json, GeoIpResponse::class.java)

        assertEquals("US", response.countryCode)
        assertEquals("United States", response.country)
        assertEquals("California", response.region)
    }
}
