package com.example.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class HashedSecret(
    val hash: String,
    val salt: String
)

object PasswordHasher {
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun hashSecret(secret: String): HashedSecret {
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val hashBytes = deriveKey(secret.toCharArray(), saltBytes)
        return HashedSecret(
            hash = toHex(hashBytes),
            salt = toHex(saltBytes)
        )
    }

    fun verifySecret(secret: String, expectedHash: String, saltHex: String): Boolean {
        return try {
            val saltBytes = fromHex(saltHex)
            val expectedBytes = fromHex(expectedHash)
            val actualBytes = deriveKey(secret.toCharArray(), saltBytes)
            MessageDigest.isEqual(expectedBytes, actualBytes)
        } catch (_: Exception) {
            false
        }
    }

    private fun deriveKey(chars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Invalid hex length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
