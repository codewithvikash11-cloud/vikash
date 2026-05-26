package com.example.data

import android.util.Base64
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object AuthHelper {

    // 1. SHA-256 Password Hashing
    fun hashPassword(password: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { String.format("%02x", it) }
        } catch (e: Exception) {
            password // Fallback
        }
    }

    // 2. Mock JWT Token Generator
    // Generates a JWT structured string: Header.Payload.Signature
    fun generateToken(username: String, isRefresh: Boolean = false): String {
        val header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
        val expTime = System.currentTimeMillis() + if (isRefresh) (7 * 24 * 60 * 60 * 1000) else (60 * 60 * 1000) // 1 Hour else 7 days
        val payload = "{\"sub\":\"$username\",\"exp\":$expTime,\"iat\":${System.currentTimeMillis()},\"type\":\"${if (isRefresh) "refresh" else "access"}\"}"
        
        val encodedHeader = Base64.encodeToString(header.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
        val encodedPayload = Base64.encodeToString(payload.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
        
        // Secret token signature
        val rawSignature = "$encodedHeader.$encodedPayload.rangilo_secret_salt_2026"
        val signatureHash = hashPassword(rawSignature)
        val encodedSignature = Base64.encodeToString(signatureHash.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
        
        return "$encodedHeader.$encodedPayload.$encodedSignature"
    }

    fun decodeTokenUsername(token: String): String? {
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payloadBytes = Base64.decode(parts[1], Base64.NO_WRAP or Base64.URL_SAFE)
                val payloadString = String(payloadBytes)
                // Simply extract "sub" claim
                val subKey = "\"sub\":\""
                val indexOfSub = payloadString.indexOf(subKey)
                if (indexOfSub != -1) {
                    val start = indexOfSub + subKey.length
                    val end = payloadString.indexOf("\"", start)
                    payloadString.substring(start, end)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun isTokenExpired(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payloadBytes = Base64.decode(parts[1], Base64.NO_WRAP or Base64.URL_SAFE)
                val payloadString = String(payloadBytes)
                val expKey = "\"exp\":"
                val indexOfExp = payloadString.indexOf(expKey)
                if (indexOfExp != -1) {
                    val start = indexOfExp + expKey.length
                    val end = payloadString.indexOf(",", start)
                    val expTime = payloadString.substring(start, end).trim().toLong()
                    System.currentTimeMillis() > expTime
                } else true
            } else true
        } catch (e: Exception) {
            true
        }
    }

    // 3. Validation Utils
    fun validatePhone(phone: String): Boolean {
        val cleaned = phone.trim().replace(" ", "").replace("-", "")
        // Matches standard international phone with 10 digits
        return cleaned.matches(Regex("^[6-9]\\d{9}$")) || cleaned.matches(Regex("^\\+?\\d{10,13}$"))
    }

    fun validateEmail(email: String): Boolean {
        if (email.isEmpty()) return true // email is optional
        return email.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"))
    }

    fun validateUsername(username: String): Boolean {
        return username.length in 3..15 && username.matches(Regex("^[a-zA-Z0-9_]+$"))
    }

    fun validatePassword(password: String): Boolean {
        return password.length >= 6
    }

    // 4. In-Memory Rate Limiter (Brute Force Protection)
    private val loginAttempts = ConcurrentHashMap<String, Int>()
    private val blockTimes = ConcurrentHashMap<String, Long>()

    fun isLockedOut(identifier: String): Boolean {
        val blockUntil = blockTimes[identifier] ?: return false
        if (System.currentTimeMillis() < blockUntil) {
            return true
        } else {
            // Block expired
            blockTimes.remove(identifier)
            loginAttempts.remove(identifier)
            return false
        }
    }

    fun getRemainingLockTime(identifier: String): Int {
        val blockUntil = blockTimes[identifier] ?: return 0
        val diff = blockUntil - System.currentTimeMillis()
        return if (diff > 0) (diff / 1000).toInt() else 0
    }

    fun recordFailedAttempt(identifier: String) {
        val attempts = (loginAttempts[identifier] ?: 0) + 1
        loginAttempts[identifier] = attempts
        if (attempts >= 5) {
            // Block for 30 seconds
            blockTimes[identifier] = System.currentTimeMillis() + 30000
        }
    }

    fun recordSuccess(identifier: String) {
        loginAttempts.remove(identifier)
        blockTimes.remove(identifier)
    }
}
