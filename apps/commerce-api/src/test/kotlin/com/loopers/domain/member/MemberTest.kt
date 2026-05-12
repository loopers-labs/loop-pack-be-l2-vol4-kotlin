package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.junit.Assert
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberTest {

    @DisplayName("요구사항을 모두 충족하면 정상적으로 생성된다.")
    @Test
    fun createsMember_whenRequiredFieldsAreProvided() {
        val loginId = "loopers123"
        val password = "encodedPassword"
        val name = "gunyoung"
        val birthDate = LocalDate.of(1970, 1, 1)
        val email = "loopers@gmail.com"

        val member = Member(
            loginId = loginId,
            password = password,
            name = name,
            birthDate = birthDate,
            email = email,
        )

        assertAll(
            { assertEquals(loginId, member.loginId) },
            { assertEquals(password, member.password) },
            { assertEquals(name, member.name) },
            { assertEquals(birthDate, member.birthDate) },
            { assertEquals(email, member.email) },
        )
    }

    @DisplayName("이름에 특수문자, 숫자가 들어가면 실패")
    @ParameterizedTest
    @ValueSource(strings = ["gunyoung12", "gunyoung$!"])
    fun throwsBadRequest_whenNameContainsNonLetters(name: String) {
        val result = assertThrows<CoreException> {
            Member(
                loginId = "loopers123",
                password = "encodedPassword",
                name = name,
                birthDate = LocalDate.of(1970, 1, 1),
                email = "loopers@gmail.com",
            )
        }

        Assert.assertEquals(result.errorType, ErrorType.BAD_REQUEST)
    }
}
