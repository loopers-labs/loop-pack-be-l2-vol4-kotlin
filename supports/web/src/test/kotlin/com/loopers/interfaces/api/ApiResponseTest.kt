package com.loopers.interfaces.api

import com.loopers.support.fixture.WebSupportTestFixtures.badRequestException
import com.loopers.support.fixture.WebSupportTestFixtures.testBody
import com.loopers.support.fixture.TestErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.http.HttpStatus

class ApiResponseTest {
    @DisplayName("성공 응답은 success 상태, HTTP status, data, timestamp를 가진다.")
    @Test
    fun successResponseHasSuccessStatusDataAndTimestamp() {
        // arrange
        val data = testBody()

        // act
        val response = ApiResponse.success(data)

        // assert
        assertAll(
            { assertThat(response.isSuccess).isTrue() },
            { assertThat(response.status).isEqualTo(HttpStatus.OK.value()) },
            { assertThat(response.code).isNull() },
            { assertThat(response.message).isNull() },
            { assertThat(response.data).isEqualTo(data) },
            { assertThat(response.timestamp).isNotNull() },
        )
    }

    @DisplayName("실패 응답은 exception의 status, code, message, timestamp를 가진다.")
    @Test
    fun failResponseHasExceptionStatusCodeMessageAndTimestamp() {
        // arrange
        val exception = badRequestException(TestErrorCode.INVALID_VALUE)

        // act
        val response = ApiResponse.fail(exception)

        // assert
        assertAll(
            { assertThat(response.isSuccess).isFalse() },
            { assertThat(response.status).isEqualTo(HttpStatus.BAD_REQUEST.value()) },
            { assertThat(response.code).isEqualTo("TEST:INVALID_VALUE") },
            { assertThat(response.message).isEqualTo(TestErrorCode.INVALID_VALUE.message) },
            { assertThat(response.data).isNull() },
            { assertThat(response.timestamp).isNotNull() },
        )
    }
}
