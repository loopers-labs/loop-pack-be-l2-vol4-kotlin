package com.loopers.support.error

import com.loopers.support.fixture.WebSupportTestFixtures.badRequestException
import com.loopers.support.fixture.WebSupportTestFixtures.notFoundException
import com.loopers.support.fixture.TestErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.http.HttpStatus

class CoreExceptionTest {
    @DisplayName("BadRequestException은 400 status와 ErrorCode를 가진다.")
    @Test
    fun badRequestExceptionHasBadRequestStatusAndErrorCode() {
        // arrange
        val errorCode = TestErrorCode.INVALID_VALUE

        // act
        val exception = badRequestException(errorCode)

        // assert
        assertAll(
            { assertThat(exception.status).isEqualTo(HttpStatus.BAD_REQUEST) },
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
        val exception = notFoundException(
            errorCode = TestErrorCode.NOT_FOUND,
            customMessage = customMessage,
        )

        // assert
        assertAll(
            { assertThat(exception.status).isEqualTo(HttpStatus.NOT_FOUND) },
            { assertThat(exception.errorCode).isEqualTo(TestErrorCode.NOT_FOUND) },
            { assertThat(exception.message).isEqualTo(customMessage) },
        )
    }
}
