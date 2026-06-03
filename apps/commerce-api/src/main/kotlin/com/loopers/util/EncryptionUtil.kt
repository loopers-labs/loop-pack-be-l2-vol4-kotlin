package com.loopers.util

import java.security.MessageDigest

object EncryptionUtil {
    fun encode(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
