package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UserV1DtoTest {
    @DisplayName("MeResponse.from 변환 시, ")
    @Nested
    inner class MeResponseFrom {
        @DisplayName("한글 이름은 마지막 글자만 별표로 가린다.")
        @Test
        fun masksLastCharacter_whenKoreanName() {
            // arrange
            val info = userInfoWith(name = "선데이")

            // act
            val response = UserV1Dto.GetUserInfoResponse.from(info)

            // assert
            assertThat(response.name).isEqualTo("선데*")
        }

        @DisplayName("영문 이름도 마지막 글자만 별표로 가린다.")
        @Test
        fun masksLastCharacter_whenEnglishName() {
            // arrange
            val info = userInfoWith(name = "John")

            // act
            val response = UserV1Dto.GetUserInfoResponse.from(info)

            // assert
            assertThat(response.name).isEqualTo("Joh*")
        }

        @DisplayName("한 글자 이름은 별표 단독으로 반환한다.")
        @Test
        fun returnsAsterisk_whenSingleCharacterName() {
            // arrange
            val info = userInfoWith(name = "A")

            // act
            val response = UserV1Dto.GetUserInfoResponse.from(info)

            // assert
            assertThat(response.name).isEqualTo("*")
        }

        private fun userInfoWith(name: String): UserInfo = UserInfo(
            id = 1L,
            loginId = "seondays",
            name = name,
            birthDate = LocalDate.of(1990, 1, 1),
            email = "seondays@example.com",
        )
    }
}
