package com.multiregionvpn.network

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoIpServiceTest {

    @Test
    fun `GeoIpResponse mapping should work with country_code`() {
        val json = """
            {
                "country_code": "US",
                "country": "United States",
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
