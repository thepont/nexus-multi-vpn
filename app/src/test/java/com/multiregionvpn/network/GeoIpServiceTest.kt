package com.multiregionvpn.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoIpServiceTest {

    @Test
    fun test_GeoIpResponse_parsing() {
        // Given a JSON response from ipwho.is
        val json = """
            {
                "ip": "8.8.8.8",
                "success": true,
                "country": "United States",
                "country_code": "US",
                "region": "California"
            }
        """.trimIndent()

        // When parsing it into GeoIpResponse
        val response = Gson().fromJson(json, GeoIpResponse::class.java)

        // Then the fields should be correctly mapped using @SerializedName
        assertEquals("US", response.countryCode)
        assertEquals("United States", response.country)
        assertEquals("California", response.region)
    }
}
