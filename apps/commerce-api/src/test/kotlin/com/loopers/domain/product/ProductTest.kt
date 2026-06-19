package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class ProductTest {
    @DisplayName("상품 생성 시, ")
    @Nested
    inner class CreateProduct {
        @DisplayName("모든 값이 유효하면 정상적으로 생성된다.")
        @Test
        fun createProduct_whenAllFieldsAreValid() {
            // arrange
            val brandId = 1L
            val name = "Loopers T-Shirt"
            val description = "매일 입기 좋은 티셔츠"
            val price = ProductPrice(10_000L)

            // act
            val product = Product(
                brandId = brandId,
                name = name,
                description = description,
                price = price,
            )

            // assert
            assertAll(
                { assertThat(product.brandId).isEqualTo(brandId) },
                { assertThat(product.name).isEqualTo(name) },
                { assertThat(product.description).isEqualTo(description) },
                { assertThat(product.price).isEqualTo(price) },
            )
        }

        @DisplayName("브랜드 ID가 1보다 작으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenBrandIdIsLessThanOne() {
            // act & assert
            val result = assertThrows<CoreException> {
                newProductWith(brandId = 0L)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("상품명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            // act & assert
            val result = assertThrows<CoreException> {
                newProductWith(name = " ")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("상품 설명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenDescriptionIsBlank() {
            // act & assert
            val result = assertThrows<CoreException> {
                newProductWith(description = " ")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("상품명 변경 시, ")
    @Nested
    inner class RenameProduct {
        @DisplayName("상품명이 유효하면 상품명이 변경된다.")
        @Test
        fun renameProduct_whenNameIsValid() {
            // arrange
            val product = newProductWith()

            // act
            product.rename(name = "Loopers Hoodie")

            // assert
            assertThat(product.name).isEqualTo("Loopers Hoodie")
        }

        @DisplayName("상품명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            // arrange
            val product = newProductWith()

            // act & assert
            val result = assertThrows<CoreException> {
                product.rename(name = " ")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("상품 설명 변경 시, ")
    @Nested
    inner class ChangeDescription {
        @DisplayName("상품 설명이 유효하면 상품 설명이 변경된다.")
        @Test
        fun changeDescription_whenDescriptionIsValid() {
            // arrange
            val product = newProductWith()

            // act
            product.changeDescription(description = "새로운 상품 설명")

            // assert
            assertThat(product.description).isEqualTo("새로운 상품 설명")
        }

        @DisplayName("상품 설명이 비어있으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenDescriptionIsBlank() {
            // arrange
            val product = newProductWith()

            // act & assert
            val result = assertThrows<CoreException> {
                product.changeDescription(description = " ")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("상품 가격 변경 시, ")
    @Nested
    inner class ChangePrice {
        @DisplayName("가격이 유효하면 상품 가격이 변경된다.")
        @Test
        fun changePrice_whenPriceIsValid() {
            // arrange
            val product = newProductWith()

            // act
            product.changePrice(price = ProductPrice(20_000L))

            // assert
            assertThat(product.price).isEqualTo(ProductPrice(20_000L))
        }
    }

    private fun newProductWith(
        brandId: Long = 1L,
        name: String = "Loopers T-Shirt",
        description: String = "매일 입기 좋은 티셔츠",
        price: ProductPrice = ProductPrice(10_000L),
    ) = Product(
        brandId = brandId,
        name = name,
        description = description,
        price = price,
    )
}
