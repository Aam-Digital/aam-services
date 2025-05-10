package com.aamdigital.aambackendservice.common.crypto.core

import org.springframework.boot.context.properties.ConfigurationProperties
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

@ConfigurationProperties("crypto-configuration")
data class CryptoConfig(
    val secret: String
)

data class EncryptedData(val iv: String, val data: String)

class CryptoService(
    private val config: CryptoConfig
) {
    companion object {
        val secureRandom = java.security.SecureRandom()
    }

    fun decrypt(data: EncryptedData): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(getHash(config.secret), "AES")
        val iv = IvParameterSpec(hexStringToByteArray(data.iv))

        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
        val decryptedBytes = cipher.doFinal(hexStringToByteArray(data.data))

        return String(decryptedBytes)
    }

    fun encrypt(text: String): EncryptedData {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val ivBytes = ByteArray(16).also { secureRandom.nextBytes(it) }
        val secretKey = SecretKeySpec(getHash(config.secret), "AES")

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(ivBytes))
        val encryptedBytes = cipher.doFinal(text.toByteArray())

        return EncryptedData(
            iv = byteArrayToHexString(ivBytes),
            data = byteArrayToHexString(encryptedBytes)
        )
    }

    private fun getHash(text: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray())
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
        }
        return data
    }

    private fun byteArrayToHexString(array: ByteArray): String {
        return array.joinToString("") { byte -> "%02x".format(byte and 0xFF.toByte()) }
    }
}
