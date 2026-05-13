package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class PasswordPolicyTest {
    @DisplayName("패스워드가 8자리 미만 16자리 초과면 실패")
    @ParameterizedTest
    @ValueSource(strings = ["pass", "12345678,12345678"])
    fun throwsBadRequest_whenPasswordLengthIsInvalid(password: String) {
        val result = assertThrows<CoreException> {
            PasswordPolicy.validate(rawPassword = password)
        }

        assertEquals(ErrorType.BAD_REQUEST, result.errorType)
    }
}
