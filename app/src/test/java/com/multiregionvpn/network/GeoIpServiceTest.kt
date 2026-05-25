package com.multiregionvpn.network

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoIpServiceTest {

    @Test
    fun testGeoIpResponseParsing() {
        val json = """
            {
                "ip": "8.8.8.8",
                "success": true,
                "type": "IPv4",
                "continent": "North America",
                "continent_code": "NA",
                "country": "United States",
                "country_code": "US",
                "region": "California",
                "region_code": "CA",
                "city": "Mountain View",
                "latitude": 37.4223,
                "longitude": -122.0847,
                "is_eu": false,
                "postal": "94043",
                "calling_code": "1",
                "capital": "Washington D.C.",
                "borders": "CAN,MEX"
            }
        """.trimIndent()

        val gson = Gson()
        val response = gson.fromJson(json, GeoIpResponse::class.java)

        assertEquals("US", response.countryCode)
        assertEquals("United States", response.country)
        assertEquals("California", response.region)
    }
}
