package com.loopers.domain.event

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class EventTest {
    @Test
    fun eventIsActiveAtInclusiveStartAndExclusiveEnd() {
        val event = Event(
            name = "Summer coupon event",
            startsAt = LocalDateTime.of(2026, 7, 3, 10, 0),
            endsAt = LocalDateTime.of(2026, 7, 3, 18, 0),
        )

        assertAll(
            { assertThat(event.isActive(LocalDateTime.of(2026, 7, 3, 9, 59, 59))).isFalse() },
            { assertThat(event.isActive(LocalDateTime.of(2026, 7, 3, 10, 0))).isTrue() },
            { assertThat(event.isActive(LocalDateTime.of(2026, 7, 3, 17, 59, 59))).isTrue() },
            { assertThat(event.isActive(LocalDateTime.of(2026, 7, 3, 18, 0))).isFalse() },
        )
    }

    @Test
    fun eventRejectsBlankNameAndInvalidPeriod() {
        val ex = assertThrows<CoreException> {
            Event(
                name = " ",
                startsAt = LocalDateTime.of(2026, 7, 3, 18, 0),
                endsAt = LocalDateTime.of(2026, 7, 3, 10, 0),
            )
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}
