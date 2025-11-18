package com.multiregionvpn.data.security

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles encryption/decryption of provider credentials.
 * Uses AES-256-GCM for encryption.
 * 
 * TODO: In production, use Android Keystore for key management.
 */
@Singleton
class CredentialEncryption @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val algorithm = "AES/GCM/NoPadding"
    private val keySize = 256

    /**
     * Encrypts credential data.
     * Returns Base64-encoded encrypted blob.
     */
    fun encrypt(data: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        
        // Prepend IV to encrypted data
        return iv + encrypted
    }

    /**
     * Decrypts credential data.
     * Expects Base64-encoded encrypted blob.
     */
    fun decrypt(encrypted: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(algorithm)
        
        // Extract IV (first 12 bytes for GCM)
        val iv = encrypted.sliceArray(0 until 12)
        val encryptedData = encrypted.sliceArray(12 until encrypted.size)
        
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.encoded, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(encryptedData)
    }

    private fun getOrCreateKey(): SecretKey {
        // TODO: Use Android Keystore for production
        // For now, generate a key and store it in SharedPreferences (not secure, but works for development)
        val prefs = context.getSharedPreferences("credential_keys", Context.MODE_PRIVATE)
        val keyString = prefs.getString("aes_key", null)
        
        return if (keyString != null) {
            val keyBytes = Base64.decode(keyString, Base64.DEFAULT)
            SecretKeySpec(keyBytes, "AES")
        } else {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(keySize)
            val key = keyGenerator.generateKey()
            
            // Store key (insecure - use Keystore in production)
            prefs.edit().putString("aes_key", Base64.encodeToString(key.encoded, Base64.DEFAULT)).apply()
            key
        }
    }
}

