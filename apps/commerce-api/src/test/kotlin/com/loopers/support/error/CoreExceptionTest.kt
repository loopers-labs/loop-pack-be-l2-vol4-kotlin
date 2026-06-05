package com.loopers.support.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CoreExceptionTest {
    @Test
    fun `별도_메시지가_없으면_ErrorType_메시지를_사용한다`() {
        val errorTypes = ErrorType.entries

        errorTypes.forEach { errorType ->
            val exception = CoreException(errorType)

            assertThat(exception.message).isEqualTo(errorType.message)
        }
    }

    @Test
    fun `별도_메시지가_있으면_해당_메시지를_사용한다`() {
        val customMessage = "custom message"

        val exception = CoreException(ErrorType.INTERNAL_ERROR, customMessage)

        assertThat(exception.message).isEqualTo(customMessage)
    }

    @Test
    fun `원인_예외가_있으면_cause로_보존한다`() {
        val cause = IllegalArgumentException("root cause")

        val exception = CoreException(ErrorType.INTERNAL_ERROR, "custom message", cause)

        assertThat(exception.cause).isSameAs(cause)
    }
}
