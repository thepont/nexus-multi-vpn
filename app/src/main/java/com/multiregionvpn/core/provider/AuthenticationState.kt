package com.multiregionvpn.core.provider

/**
 * State of authentication with a provider.
 */
sealed class AuthenticationState {
    object Authenticating : AuthenticationState()
    data class Authenticated(val token: String? = null) : AuthenticationState()
    data class Failed(val error: String) : AuthenticationState()
}


