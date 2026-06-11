package com.loopers.domain.brand

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BrandTest {

    @DisplayName("Brand.create 호출 시, ")
    @Nested
    inner class Create {
        @DisplayName("name과 description이 유효하면, Brand를 생성한다.")
        @Test
        fun createsBrand_whenValid() {
            // act
            val brand = Brand.create(name = "Nike", description = "Just do it")

            // assert
            assertThat(brand.id).isEqualTo(0L)
            assertThat(brand.name).isEqualTo("Nike")
            assertThat(brand.description).isEqualTo("Just do it")
        }

        @DisplayName("name이 blank이면, IllegalArgumentException이 발생한다.")
        @Test
        fun throwsException_whenNameBlank() {
            // act
            val result = assertThrows<IllegalArgumentException> {
                Brand.create(name = " ", description = "Just do it")
            }

            // assert
            assertThat(result.message).contains("브랜드 이름")
        }

        @DisplayName("description이 blank이면, IllegalArgumentException이 발생한다.")
        @Test
        fun throwsException_whenDescriptionBlank() {
            // act
            val result = assertThrows<IllegalArgumentException> {
                Brand.create(name = "Nike", description = "")
            }

            // assert
            assertThat(result.message).contains("브랜드 설명")
        }
    }

    @DisplayName("update 호출 시, ")
    @Nested
    inner class Update {
        @DisplayName("새로운 name과 description으로 갱신된 Brand를 반환한다(id 보존).")
        @Test
        fun returnsUpdatedBrand_whenValid() {
            // arrange
            val brand = Brand(id = 10L, name = "Nike", description = "Just do it")

            // act
            val updated = brand.update(name = "Nike2", description = "new")

            // assert
            assertThat(updated.id).isEqualTo(10L)
            assertThat(updated.name).isEqualTo("Nike2")
            assertThat(updated.description).isEqualTo("new")
        }

        @DisplayName("update에 blank를 넘기면, IllegalArgumentException이 발생한다.")
        @Test
        fun throwsException_whenBlank() {
            // arrange
            val brand = Brand(id = 10L, name = "Nike", description = "Just do it")

            // act
            val result = assertThrows<IllegalArgumentException> {
                brand.update(name = "", description = "new")
            }

            // assert
            assertThat(result).isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
