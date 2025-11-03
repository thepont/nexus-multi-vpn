package com.multiregionvpn.network

import com.google.gson.annotations.SerializedName

data class NordServer(
    val hostname: String,
    val country: String,
    @SerializedName("region")
    val region: String?,
    @SerializedName("ip_address")
    val ipAddress: String?,
    val load: Int?,
    val features: List<String>?
)
