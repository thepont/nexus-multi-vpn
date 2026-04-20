package com.multiregionvpn.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoIpServiceTest {

    @Test
    fun `GeoIpResponse should map country_code correctly from JSON`() {
        val json = """{"country_code": "US", "country": "United States", "region": "CA"}"""
        val response = Gson().fromJson(json, GeoIpResponse::class.java)

        assertEquals("US", response.countryCode)
        assertEquals("United States", response.country)
        assertEquals("CA", response.region)
    }
}
