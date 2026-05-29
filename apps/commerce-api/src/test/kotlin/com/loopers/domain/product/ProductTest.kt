package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
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
            val stock = Stock(10)
            val likeCount = 3

            // act
            val product = Product(
                brandId = brandId,
                name = name,
                description = description,
                price = price,
                stock = stock,
                likeCount = likeCount,
            )

            // assert
            assertAll(
                { assertThat(product.brandId).isEqualTo(brandId) },
                { assertThat(product.name).isEqualTo(name) },
                { assertThat(product.description).isEqualTo(description) },
                { assertThat(product.price).isEqualTo(price) },
                { assertThat(product.stock).isEqualTo(stock) },
                { assertThat(product.likeCount).isEqualTo(likeCount) },
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

        @DisplayName("좋아요 수가 음수이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLikeCountIsNegative() {
            // act & assert
            val result = assertThrows<CoreException> {
                newProductWith(likeCount = -1)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("재고 차감 가능 여부 검증 시, ")
    @Nested
    inner class ValidateStockDeductible {
        @DisplayName("재고가 충분하면 예외가 발생하지 않는다.")
        @Test
        fun doesNotThrow_whenStockIsEnough() {
            // arrange
            val product = newProductWith(stock = Stock(10))

            // act & assert
            assertDoesNotThrow {
                product.validateStockDeductible(StockQuantity(3))
            }
        }

        @DisplayName("재고와 수량이 같으면 예외가 발생하지 않는다.")
        @Test
        fun doesNotThrow_whenStockEqualsQuantity() {
            // arrange
            val product = newProductWith(stock = Stock(3))

            // act & assert
            assertDoesNotThrow {
                product.validateStockDeductible(StockQuantity(3))
            }
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenStockIsInsufficient() {
            // arrange
            val product = newProductWith(stock = Stock(2))

            // act & assert
            val result = assertThrows<CoreException> {
                product.validateStockDeductible(StockQuantity(3))
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("좋아요 감소 가능 여부 검증 시, ")
    @Nested
    inner class ValidateLikeCountDecreasable {
        @DisplayName("좋아요 수가 0보다 크면 예외가 발생하지 않는다.")
        @Test
        fun doesNotThrow_whenLikeCountIsPositive() {
            // arrange
            val product = newProductWith(likeCount = 1)

            // act & assert
            assertDoesNotThrow {
                product.validateLikeCountDecreasable()
            }
        }

        @DisplayName("좋아요 수가 0이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenLikeCountIsZero() {
            // arrange
            val product = newProductWith(likeCount = 0)

            // act & assert
            val result = assertThrows<CoreException> {
                product.validateLikeCountDecreasable()
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("상품 품절 여부 확인 시, ")
    @Nested
    inner class IsSoldOut {
        @DisplayName("재고가 0이면 품절이다.")
        @Test
        fun returnsTrue_whenStockIsZero() {
            // arrange
            val product = newProductWith(stock = Stock(0))

            // act
            val result = product.isSoldOut()

            // assert
            assertThat(result).isTrue()
        }

        @DisplayName("재고가 1 이상이면 품절이 아니다.")
        @Test
        fun returnsFalse_whenStockIsGreaterThanZero() {
            // arrange
            val product = newProductWith(stock = Stock(1))

            // act
            val result = product.isSoldOut()

            // assert
            assertThat(result).isFalse()
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

    @DisplayName("상품 재고 조정 시, ")
    @Nested
    inner class AdjustStock {
        @DisplayName("재고가 유효하면 상품 재고가 조정된다.")
        @Test
        fun adjustStock_whenStockIsValid() {
            // arrange
            val product = newProductWith(stock = Stock(10))

            // act
            product.adjustStock(stock = Stock(5))

            // assert
            assertThat(product.stock).isEqualTo(Stock(5))
        }
    }

    private fun newProductWith(
        brandId: Long = 1L,
        name: String = "Loopers T-Shirt",
        description: String = "매일 입기 좋은 티셔츠",
        price: ProductPrice = ProductPrice(10_000L),
        stock: Stock = Stock(10),
        likeCount: Int = 0,
    ) = Product(
        brandId = brandId,
        name = name,
        description = description,
        price = price,
        stock = stock,
        likeCount = likeCount,
    )
}
