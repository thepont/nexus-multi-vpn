package com.multiregionvpn.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * Retrofit interface for NordVPN API
 */
interface NordVpnApiService {
    @GET("server")
    suspend fun getServers(
        @Header("Authorization") token: String
    ): List<NordServer>
    
    @GET("configs/files/ovpn_udp/servers/{hostname}.udp.ovpn")
    suspend fun getOvpnConfig(
        @Path("hostname") hostname: String
    ): ResponseBody
}
