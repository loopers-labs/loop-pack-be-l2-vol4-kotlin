package com.loopers.domain.user

import com.loopers.fixture.user.UserFixture
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

class UserTest {
    @DisplayName("User 생성")
    @Nested
    inner class Create {
        @DisplayName("요구사항을 모두 충족하면 정상적으로 생성된다.")
        @Test
        fun createsUser_whenRequiredFieldsAreProvided() {
            val loginId = "loopers123"
            val password = "encodedPassword"
            val name = "gunyoung"
            val birthDate = LocalDate.of(1970, 1, 1)
            val email = "loopers@gmail.com"

            val user = UserFixture.createUser(
                loginId = loginId,
                password = password,
                name = name,
                birthDate = birthDate,
                email = email,
            )

            assertAll(
                { assertThat(user.id).isZero() },
                { assertThat(user.loginId).isEqualTo(loginId) },
                { assertThat(user.password).isEqualTo(password) },
                { assertThat(user.name).isEqualTo(name) },
                { assertThat(user.birthDate).isEqualTo(birthDate) },
                { assertThat(user.email).isEqualTo(email) },
            )
        }

        @DisplayName("로그인 ID 에 영문과 숫자 외 문자가 들어가면 실패")
        @ParameterizedTest
        @ValueSource(strings = [" ", "loopers-123", "loopers_123", "loopers!"])
        fun throwsBadRequest_whenLoginIdContainsNonAlphanumericCharacters(loginId: String) {
            val result = assertThrows<CoreException> {
                UserFixture.createUser(loginId = loginId)
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }

        @DisplayName("이름에 특수문자, 숫자, 공백이 들어가면 실패")
        @ParameterizedTest
        @ValueSource(strings = [" ", "gunyoung12", "gunyoung$!", "young young"])
        fun throwsBadRequest_whenNameContainsNonLetters(name: String) {
            val result = assertThrows<CoreException> {
                UserFixture.createUser(name = name)
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }

        @DisplayName("이메일 포멧이 유효하지 않으면 실패")
        @ParameterizedTest
        @ValueSource(strings = [" ", "loopers", "@gmail.com", "loopers123@fewf", "loopers@fewf."])
        fun throwsBadRequest_whenEmailFormatIsNotValid(invalidEmail: String) {
            val result = assertThrows<CoreException> {
                UserFixture.createUser(email = invalidEmail)
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }

        @DisplayName("생년월일이 유효하지 않으면 실패")
        @Test
        fun throwsBadRequest_whenBirthDateIsNotValid() {
            val invalidBirthDate = LocalDate.now().plusDays(1)
            val result = assertThrows<CoreException> {
                UserFixture.createUser(birthDate = invalidBirthDate)
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    inner class UpdatePassword {
        @DisplayName("새 암호화 비밀번호로 변경한다")
        @Test
        fun updatesPassword_whenEncodedPasswordIsProvided() {
            val user = UserFixture.createUser(password = "oldEncodedPassword")

            user.updatePassword("newEncodedPassword")

            assertThat(user.password).isEqualTo("newEncodedPassword")
        }
    }
}
