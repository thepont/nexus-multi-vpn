package com.multiregionvpn.core.vpnclient

/**
 * Exception thrown when OpenVPN authentication fails.
 * This includes invalid credentials, expired credentials, or authentication rejection.
 */
class AuthenticationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)


