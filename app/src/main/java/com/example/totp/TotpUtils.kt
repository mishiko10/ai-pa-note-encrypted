package com.example.totp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TotpUtils {
    fun generateTOTP(secret: String, timeOffsetSeconds: Long = 0): String {
        try {
            val cleanSecret = secret.replace(" ", "").uppercase()
            val decodedBytes = decodeBase32(cleanSecret) ?: return "------"
            val currentTimeSeconds = System.currentTimeMillis() / 1000
            val time = (currentTimeSeconds + timeOffsetSeconds) / 30
            return computeTotp(decodedBytes, time)
        } catch (e: Exception) {
            return "------"
        }
    }

    private fun decodeBase32(base32: String): ByteArray? {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val clean = base32.trim().replace("-", "").replace(" ", "").uppercase()
        if (clean.isEmpty()) return null
        
        val bytes = ByteArray(clean.length * 5 / 8)
        var i = 0
        var index = 0
        var currByte = 0
        
        for (c in clean) {
            val lookup = base32Chars.indexOf(c)
            if (lookup == -1) continue // Skip padding or invalid characters
            
            currByte = (currByte shl 5) or lookup
            index += 5
            
            if (index >= 8) {
                bytes[i++] = ((currByte shr (index - 8)) and 0xFF).toByte()
                index -= 8
            }
        }
        return bytes.copyOfRange(0, i)
    }

    private fun computeTotp(key: ByteArray, time: Long): String {
        val data = ByteArray(8)
        var value = time
        for (i in 7 downTo 0) {
            data[i] = (value and 0xFF).toByte()
            value = value shr 8
        }

        val signKey = SecretKeySpec(key, "HmacSHA1")
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(signKey)
        val hash = mac.doFinal(data)

        val offset = hash[hash.size - 1].toInt() and 0xF
        val truncatedHash = (
            ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        )

        val pinValue = truncatedHash % 1000000
        return String.format("%06d", pinValue)
    }
}
