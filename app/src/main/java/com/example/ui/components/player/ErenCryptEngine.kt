package com.example.ui.components.player

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object ErenCryptEngine {
    // Eren's special cryptographic master salt to secure the signature and keys
    private const val EREN_MASTER_SALT = "Eren_Ultimate_Crypt_Master_2026_#"

    /**
     * Derives a 16-byte secure AES key from Eren's Master Salt + User input key
     */
    private fun deriveKey(userKey: String): SecretKeySpec {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest((EREN_MASTER_SALT + userKey).toByteArray(Charsets.UTF_8))
        // Take first 16 bytes for AES-128
        val keyBytes = hash.copyOf(16)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Derives a 16-byte IV (Initialization Vector) from the derivation keys
     */
    private fun deriveIV(userKey: String): IvParameterSpec {
        val hash = MessageDigest.getInstance("MD5")
            .digest((userKey + EREN_MASTER_SALT).toByteArray(Charsets.UTF_8))
        return IvParameterSpec(hash)
    }

    /**
     * Encrypts the raw text with a custom Eren-specialized key, returning a signed Base64 string
     */
    fun encrypt(rawText: String, userKey: String): String {
        if (rawText.isEmpty()) return ""
        val keySpec = deriveKey(userKey)
        val ivSpec = deriveIV(userKey)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        
        val encryptedBytes = cipher.doFinal(rawText.toByteArray(Charsets.UTF_8))
        val base64Cipher = Base64.encodeToString(encryptedBytes, Base64.DEFAULT or Base64.NO_WRAP)
        
        // Custom envelope prefix to indicate it is Eren's special encrypted file signature format
        return "EREN_SECURE_V1[$base64Cipher]"
    }

    /**
     * Decrypts the signed Base64 string back to raw text
     */
    fun decrypt(encryptedText: String, userKey: String): String {
        if (encryptedText.isEmpty()) return ""
        val cleaned = if (encryptedText.startsWith("EREN_SECURE_V1[") && encryptedText.endsWith("]")) {
            encryptedText.substring("EREN_SECURE_V1[".length, encryptedText.length - 1)
        } else {
            encryptedText
        }

        val keySpec = deriveKey(userKey)
        val ivSpec = deriveIV(userKey)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

        val encryptedBytes = Base64.decode(cleaned, Base64.DEFAULT)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Generates a unique, copyable, authentic Digital Signature Hash for file content or note
     */
    fun generateFileSignature(content: String, userKey: String): String {
        val merged = "$content|KEY:$userKey|OWNER:Eren|YEAR:2026"
        val hashBytes = MessageDigest.getInstance("SHA-256").digest(merged.toByteArray(Charsets.UTF_8))
        // Convert to nice hex string
        val hexString = hashBytes.joinToString("") { String.format("%02x", it) }
        return "EREN-SIG-${hexString.take(24).uppercase()}"
    }

    /**
     * Verifies if a given content + key matches the signature hash format
     */
    fun verifyFileSignature(content: String, userKey: String, signature: String): Boolean {
        if (!signature.startsWith("EREN-SIG-") || signature.length < 15) return false
        val computed = generateFileSignature(content, userKey)
        return computed == signature
    }
}
