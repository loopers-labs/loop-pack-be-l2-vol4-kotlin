package com.loopers.application.event

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class UuidV7GeneratorTest {
    @Test
    fun generateCreatesUuidV7WithRfcVariant() {
        val generator = UuidV7Generator()

        val uuid = generator.generate(Instant.ofEpochMilli(1_720_000_000_000))

        assertThat(uuid.version()).isEqualTo(7)
        assertThat(uuid.variant()).isEqualTo(2)
    }
}
