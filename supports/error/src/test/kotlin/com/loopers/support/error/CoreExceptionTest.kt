package com.loopers.support.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class CoreExceptionTest {
    @DisplayName("BadRequestException은 ErrorCode를 가진다.")
    @Test
    fun badRequestExceptionHasErrorCode() {
        // arrange
        val errorCode = TestErrorCode.INVALID_VALUE

        // act
        val exception = BadRequestException(errorCode)

        // assert
        assertAll(
            { assertThat(exception.errorCode).isEqualTo(errorCode) },
            { assertThat(exception.message).isEqualTo(errorCode.message) },
        )
    }

    @DisplayName("custom message가 주어지면 ErrorCode 기본 메시지 대신 custom message를 사용한다.")
    @Test
    fun usesCustomMessage_whenCustomMessageIsProvided() {
        // arrange
        val customMessage = "custom message"

        // act
        val exception = NotFoundException(
            errorCode = TestErrorCode.NOT_FOUND,
            customMessage = customMessage,
        )

        // assert
        assertAll(
            { assertThat(exception.errorCode).isEqualTo(TestErrorCode.NOT_FOUND) },
            { assertThat(exception.message).isEqualTo(customMessage) },
        )
    }
}

private enum class TestErrorCode(
    private val reason: String,
    override val message: String,
) : ErrorCode {
    INVALID_VALUE("INVALID_VALUE", "유효하지 않은 값입니다."),
    NOT_FOUND("NOT_FOUND", "대상을 찾을 수 없습니다."),
    ;

    override val code: String
        get() = "TEST:$reason"
}
