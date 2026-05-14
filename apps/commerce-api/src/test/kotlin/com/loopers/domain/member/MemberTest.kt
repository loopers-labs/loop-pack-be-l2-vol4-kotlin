package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.fixture.member.MemberFixture
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

            val member = MemberFixture.createMember(
                loginId = loginId,
                password = password,
                name = name,
                birthDate = birthDate,
                email = email,
            )

            assertAll(
                { assertThat(member.id).isNotNull() },
                { assertThat(member.loginId).isEqualTo(loginId) },
                { assertThat(member.password).isEqualTo(password) },
                { assertThat(member.name).isEqualTo(name) },
                { assertThat(member.birthDate).isEqualTo(birthDate) },
                { assertThat(member.email).isEqualTo(email) },
            )
        }

        @DisplayName("로그인 ID 에 영문과 숫자 외 문자가 들어가면 실패")
        @ParameterizedTest
        @ValueSource(strings = [" ", "loopers-123", "loopers_123", "loopers!"])
        fun throwsBadRequest_whenLoginIdContainsNonAlphanumericCharacters(loginId: String) {
            val result = assertThrows<CoreException> {
                MemberFixture.createMember(loginId = loginId)
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }

        @DisplayName("이름에 특수문자, 숫자, 공백이 들어가면 실패")
        @ParameterizedTest
        @ValueSource(strings = [" ", "gunyoung12", "gunyoung$!", "young young"])
        fun throwsBadRequest_whenNameContainsNonLetters(name: String) {
            val result = assertThrows<CoreException> {
                MemberFixture.createMember(name = name)
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }

        @DisplayName("이메일 포멧이 유효하지 않으면 실패")
        @ParameterizedTest
        @ValueSource(strings = [" ", "loopers", "@gmail.com", "loopers123@fewf", "loopers@fewf."])
        fun throwsBadRequest_whenEmailFormatIsNotValid(invalidEmail: String) {
            val result = assertThrows<CoreException> {
                MemberFixture.createMember(email = invalidEmail)
            }

            assertEquals(ErrorType.BAD_REQUEST, result.errorType)
        }

        @DisplayName("생년월일이 유효하지 않으면 실패")
        @Test
        fun throwsBadRequest_whenBirthDateIsNotValid() {
            val invalidBirthDate = LocalDate.now().plusDays(1)
            val result = assertThrows<CoreException> {
                MemberFixture.createMember(birthDate = invalidBirthDate)
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
            val member = MemberFixture.createMember(password = "oldEncodedPassword")

            member.updatePassword("newEncodedPassword")

            assertThat(member.password).isEqualTo("newEncodedPassword")
        }
    }
}
