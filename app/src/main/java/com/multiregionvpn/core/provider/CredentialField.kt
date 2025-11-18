package com.multiregionvpn.core.provider

/**
 * Metadata describing a credential field required by a provider.
 */
data class CredentialField(
    val key: String, // e.g., "username", "password", "api_token"
    val label: String, // e.g., "Username", "Password", "API Token"
    val isPassword: Boolean = false, // Whether this field should be masked in UI
    val isRequired: Boolean = true,
    val hint: String? = null // Optional hint text for the UI
)


