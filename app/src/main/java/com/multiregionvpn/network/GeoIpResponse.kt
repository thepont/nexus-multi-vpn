package com.multiregionvpn.network

import com.google.gson.annotations.SerializedName

data class GeoIpResponse(
    @SerializedName("country_code")
    val countryCode: String?,
    @SerializedName("country")
    val country: String?,
    @SerializedName("region")
    val region: String?
)
