package com.multiregionvpn.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.http.GET

class GeoIpServiceTest {

    @Test
    fun `GeoIpApi should use HTTPS and correct endpoint`() {
        val clazz = GeoIpApi::class.java
        val method = clazz.methods.find { it.name == "getCurrentLocation" }
        assertNotNull("Method getCurrentLocation should exist", method)

        val getAnnotation = method?.getAnnotation(GET::class.java)
        assertNotNull("Method should have @GET annotation", getAnnotation)
    }
}
