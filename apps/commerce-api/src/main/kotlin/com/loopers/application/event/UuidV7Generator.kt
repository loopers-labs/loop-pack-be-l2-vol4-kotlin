package com.loopers.application.event

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

@Component
class UuidV7Generator(
    private val random: SecureRandom = SecureRandom(),
) {
    fun generate(now: Instant = Instant.now()): UUID {
        val timestampMillis = now.toEpochMilli() and 0x0000FFFFFFFFFFFFL
        val randomA = random.nextInt(1 shl 12).toLong()
        val randomB = random.nextLong() and 0x3FFFFFFFFFFFFFFFL

        val mostSignificantBits = (timestampMillis shl 16) or 0x7000L or randomA
        val leastSignificantBits = Long.MIN_VALUE or randomB

        return UUID(mostSignificantBits, leastSignificantBits)
    }
}
