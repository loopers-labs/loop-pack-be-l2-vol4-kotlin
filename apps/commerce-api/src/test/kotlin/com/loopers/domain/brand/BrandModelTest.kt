package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BrandModelTest {
    @DisplayName("브랜드를 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("유효한 이름과 설명이면 생성된다.")
        @Test
        fun createsBrand_whenFieldsAreValid() {
            // arrange
            val name = "Nike"

            // act
            val brand = BrandModel(name = name, description = "Just do it.")

            // assert
            assertThat(brand.name).isEqualTo(name)
        }

        @DisplayName("이름이 공백이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            // act
            val exception = assertThrows<CoreException> {
                BrandModel(name = "   ", description = "")
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
