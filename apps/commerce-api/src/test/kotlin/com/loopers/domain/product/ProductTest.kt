package com.loopers.domain.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductTest {

    @DisplayName("Product.create 호출 시, ")
    @Nested
    inner class Create {
        @DisplayName("유효한 값이면 Product를 생성한다.")
        @Test
        fun createsProduct_whenValid() {
            // act
            val product = Product.create(name = "에어맥스", price = 100000L, description = "운동화", brandId = 1L)

            // assert
            assertThat(product.id).isEqualTo(0L)
            assertThat(product.name).isEqualTo("에어맥스")
            assertThat(product.price).isEqualTo(100000L)
            assertThat(product.brandId).isEqualTo(1L)
        }

        @DisplayName("name이 blank이면 IllegalArgumentException이 발생한다.")
        @Test
        fun throwsException_whenNameBlank() {
            val result = assertThrows<IllegalArgumentException> {
                Product.create(name = " ", price = 100L, description = "x", brandId = 1L)
            }
            assertThat(result.message).contains("상품 이름")
        }

        @DisplayName("price가 0 이하이면 IllegalArgumentException이 발생한다.")
        @Test
        fun throwsException_whenPriceNotPositive() {
            assertThrows<IllegalArgumentException> {
                Product.create(name = "x", price = 0L, description = "x", brandId = 1L)
            }
            assertThrows<IllegalArgumentException> {
                Product.create(name = "x", price = -1L, description = "x", brandId = 1L)
            }
        }

        @DisplayName("brandId가 0 이하이면 IllegalArgumentException이 발생한다.")
        @Test
        fun throwsException_whenBrandIdNotPositive() {
            assertThrows<IllegalArgumentException> {
                Product.create(name = "x", price = 100L, description = "x", brandId = 0L)
            }
        }
    }

    @DisplayName("update 호출 시, ")
    @Nested
    inner class Update {
        @DisplayName("name/price/description은 갱신되지만 brandId는 보존된다.")
        @Test
        fun preservesBrandId() {
            // arrange
            val product = Product(id = 10L, name = "old", price = 100L, description = "d", brandId = 5L)

            // act
            val updated = product.update(name = "new", price = 200L, description = "newD")

            // assert
            assertThat(updated.id).isEqualTo(10L)
            assertThat(updated.name).isEqualTo("new")
            assertThat(updated.price).isEqualTo(200L)
            assertThat(updated.description).isEqualTo("newD")
            assertThat(updated.brandId).isEqualTo(5L) // 보존
        }

        @DisplayName("update에 잘못된 price를 넘기면 예외가 발생한다.")
        @Test
        fun throwsException_whenInvalidPrice() {
            val product = Product(id = 10L, name = "x", price = 100L, description = "d", brandId = 1L)
            assertThrows<IllegalArgumentException> {
                product.update(name = "x", price = 0L, description = "d")
            }
        }
    }
}
