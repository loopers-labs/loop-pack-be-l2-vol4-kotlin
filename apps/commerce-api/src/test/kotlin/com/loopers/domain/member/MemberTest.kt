package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberTest {
    @DisplayName("Member 생성")
    @Nested
    inner class Creat {
        @DisplayName("요구사항을 모두 충족하면 정상적으로 생성된다.")
        @Test
        fun createsMember_whenRequiredFieldsAreProvided() {
            val loginId = "loopers123"
            val password = "encodedPassword"
            val name = "gunyoung"
            val birthDate = LocalDate.of(1970, 1, 1)
            val email = "loopers@gmail.com"

            val member = createMember(
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

        @DisplayName("이름에 특수문자, 숫자, 공백이 들어가면 실패")
        @ParameterizedTest
        @ValueSource(strings = [" ", "gunyoung12", "gunyoung$!", "young young"])
        fun throwsBadRequest_whenNameContainsNonLetters(name: String) {
            val result = assertThrows<CoreException> {
                createMember(name = name)
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }

        @DisplayName("이메일 포멧이 유효하지 않으면 실패")
        @ParameterizedTest
        @ValueSource(strings = [" ", "loopers", "@gmail.com", "loopers123@fewf", "loopers@fewf."])
        fun throwsBadRequest_whenEmailFormatIsNotValid(invalidEmail: String) {
            val result = assertThrows<CoreException> {
                createMember(email = invalidEmail)
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }

        @DisplayName("생년월일이 유효하지 않으면 실패")
        @Test
        fun throwsBadRequest_whenBirthDateIsNotValid() {
            val invalidBirthDate = LocalDate.now().plusDays(1)
            val result = assertThrows<CoreException> {
                createMember(birthDate = invalidBirthDate)
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }

        private fun createMember(
            loginId: String = "loopers123",
            password: String = "encodedPassword",
            name: String = "gunyoung",
            birthDate: LocalDate = LocalDate.of(1970, 1, 1),
            email: String = "loopers@gmail.com",
        ): Member =
            Member(
                loginId = loginId,
                password = password,
                name = name,
                birthDate = birthDate,
                email = email,
            )
    }
}
