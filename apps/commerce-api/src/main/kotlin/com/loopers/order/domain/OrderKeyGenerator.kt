package com.loopers.order.domain

import java.security.SecureRandom
import java.util.UUID

object OrderKeyGenerator {
    private val random = SecureRandom()

    fun generate(): String = uuidV7().toString()

    private fun uuidV7(): UUID {
        val timestampMs = System.currentTimeMillis()

        var msb = (timestampMs and 0xFFFFFFFFFFFFL) shl 16
        msb = msb or 0x7000L
        msb = msb or random.nextInt(0x1000).toLong()

        var lsb = random.nextLong() and 0x3FFFFFFFFFFFFFFFL
        lsb = lsb or Long.MIN_VALUE

        return UUID(msb, lsb)
    }
}
