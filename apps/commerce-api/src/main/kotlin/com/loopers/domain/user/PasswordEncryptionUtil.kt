package com.loopers.domain.user

import java.security.MessageDigest

object PasswordEncryptionUtil {
    fun encode(rawPassword: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(rawPassword.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
