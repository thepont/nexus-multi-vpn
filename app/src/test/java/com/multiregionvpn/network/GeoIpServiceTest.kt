package com.multiregionvpn.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.http.GET
import com.google.gson.annotations.SerializedName

class GeoIpServiceTest {

    @Test
    fun `GeoIpApi should use root path for ipwho is`() {
        val methods = GeoIpApi::class.java.methods
        // Find by name, handles suspend function signatures which have Continuation parameter
        val getCurrentLocation = methods.find { it.name == "getCurrentLocation" }
        assertNotNull("getCurrentLocation method not found", getCurrentLocation)

        val getAnnotation = getCurrentLocation?.getAnnotation(GET::class.java)
        assertEquals("/", getAnnotation?.value)
    }

    @Test
    fun `GeoIpResponse should have correct SerializedName for country_code`() {
        val field = GeoIpResponse::class.java.getDeclaredField("countryCode")
        val annotation = field.getAnnotation(SerializedName::class.java)
        assertNotNull("SerializedName annotation missing on countryCode field", annotation)
        assertEquals("country_code", annotation?.value)
    }
}
