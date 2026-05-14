package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserTest {
    companion object {
        private val LOGIN_ID = LoginId("user01")
        private const val PASSWORD = "Password1!"
        private val NAME = Name("홍길동")
        private val BIRTH_DATE = BirthDate("19900628")
        private val EMAIL = Email("test@test.com")
    }

    @DisplayName("User 생성 시,")
    @Nested
    inner class Create {

        @DisplayName("유효한 정보가 주어지면, 정상적으로 생성된다.")
        @Test
        fun createsUser_whenValidInfoIsProvided() {
            // act
            val user = User(loginId = LOGIN_ID, password = PASSWORD, name = NAME, birthDate = BIRTH_DATE, email = EMAIL)

            // assert
            assertThat(user.loginId).isEqualTo(LOGIN_ID)
            assertThat(user.name).isEqualTo(NAME)
            assertThat(user.birthDate).isEqualTo(BIRTH_DATE)
            assertThat(user.email).isEqualTo(EMAIL)
        }
    }
}
