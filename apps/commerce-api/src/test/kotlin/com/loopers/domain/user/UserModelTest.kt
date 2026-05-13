package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.LocalDate

class UserModelTest {
    @DisplayName("회원 가입 시, ")
    @Nested
    inner class CreateUser {
        @DisplayName("모든 값이 유효하면 정상적으로 가입된다.")
        @Test
        fun createUser_whenAllFieldsAreValid() {
            // arrange
            val validLoginId = "seondays"
            val validPassword = "password"
            val validName = "선데이"
            val validBirthDate = LocalDate.of(1990, 1, 1)
            val validEmail = "seondays@example.com"

            // act
            val user = UserModel(
                loginId = validLoginId,
                password = validPassword,
                name = validName,
                birthDate = validBirthDate,
                email = validEmail,
            )

            // assert
            assertAll(
                { assertThat(user.loginId).isEqualTo(validLoginId) },
                { assertThat(user.password).isEqualTo(validPassword) },
                { assertThat(user.name).isEqualTo(validName) },
                { assertThat(user.birthDate).isEqualTo(validBirthDate) },
                { assertThat(user.email).isEqualTo(validEmail) },
            )
        }
    }
}
