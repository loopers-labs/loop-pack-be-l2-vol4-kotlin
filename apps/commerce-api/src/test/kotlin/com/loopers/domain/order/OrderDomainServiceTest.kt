package com.loopers.domain.order

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.withId
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.ZonedDateTime

class OrderDomainServiceTest {
    @DisplayName("주문을 생성할 때,")
    @Nested
    inner class Create {
        @DisplayName("여러 상품 주문을 생성하고 각 상품 재고를 차감한다.")
        @Test
        fun createsOrderAndDeductsStocks() {
            // arrange
            val service = OrderDomainService()
            val firstStock = ProductStockModel(productId = 10L, quantity = 10)
            val secondStock = ProductStockModel(productId = 20L, quantity = 5)

            // act
            val order = service.create(
                userId = 1L,
                items = listOf(
                    orderProduct(productId = 10L, price = "120000.00", stock = firstStock, quantity = 2),
                    orderProduct(productId = 20L, price = "30000.00", stock = secondStock, quantity = 1),
                ),
            )

            // assert
            assertAll(
                { assertThat(order.items).hasSize(2) },
                { assertThat(order.totalPrice).isEqualByComparingTo(BigDecimal("270000.00")) },
                { assertThat(firstStock.quantity).isEqualTo(8) },
                { assertThat(secondStock.quantity).isEqualTo(4) },
            )
        }

        @DisplayName("재고가 부족하면 CONFLICT 예외가 발생하고 어떤 재고도 차감하지 않는다.")
        @Test
        fun throwsConflictAndDoesNotDeductAnyStock_whenStockIsInsufficient() {
            // arrange
            val service = OrderDomainService()
            val firstStock = ProductStockModel(productId = 10L, quantity = 10)
            val secondStock = ProductStockModel(productId = 20L, quantity = 5)

            // act
            val exception = assertThrows<CoreException> {
                service.create(
                    userId = 1L,
                    items = listOf(
                        orderProduct(productId = 10L, price = "120000.00", stock = firstStock, quantity = 2),
                        orderProduct(productId = 20L, price = "30000.00", stock = secondStock, quantity = 99),
                    ),
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(firstStock.quantity).isEqualTo(10)
            assertThat(secondStock.quantity).isEqualTo(5)
        }

        @DisplayName("같은 상품이 중복으로 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenSameProductIsDuplicated() {
            // arrange
            val service = OrderDomainService()
            val stock = ProductStockModel(productId = 10L, quantity = 10)

            // act
            val exception = assertThrows<CoreException> {
                service.create(
                    userId = 1L,
                    items = listOf(
                        orderProduct(productId = 10L, price = "120000.00", stock = stock, quantity = 1),
                        orderProduct(productId = 10L, price = "120000.00", stock = stock, quantity = 1),
                    ),
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(stock.quantity).isEqualTo(10)
        }

        @DisplayName("쿠폰과 함께 주문을 생성하면 할인 적용과 쿠폰 사용 처리가 함께 일어난다.")
        @Test
        fun appliesCouponAndMarksItUsed() {
            // arrange
            val service = OrderDomainService()
            val now = ZonedDateTime.now()
            val coupon = coupon(discountValue = "1000.00", expiredAt = now.plusDays(1))
            val userCoupon = UserCouponModel(userId = 1L, couponId = coupon.id).withId(200L)

            // act
            val order = service.create(
                userId = 1L,
                items = listOf(orderProduct(productId = 10L, price = "120000.00", quantity = 2)),
                couponApplication = OrderDomainService.CouponApplication(coupon = coupon, userCoupon = userCoupon),
                now = now,
            )

            // assert
            assertAll(
                { assertThat(order.discountAmount).isEqualByComparingTo(BigDecimal("1000.00")) },
                { assertThat(order.paidPrice).isEqualByComparingTo(BigDecimal("239000.00")) },
                { assertThat(order.userCouponId).isEqualTo(200L) },
                { assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED) },
            )
        }

        @DisplayName("최소 주문 금액 미달 쿠폰으로 주문을 생성하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenOrderAmountBelowCouponMinimum() {
            // arrange
            val service = OrderDomainService()
            val now = ZonedDateTime.now()
            val stock = ProductStockModel(productId = 10L, quantity = 10)
            val coupon = coupon(
                discountValue = "1000.00",
                minOrderAmount = BigDecimal("99999999.00"),
                expiredAt = now.plusDays(1),
            )
            val userCoupon = UserCouponModel(userId = 1L, couponId = coupon.id).withId(200L)

            // act
            val exception = assertThrows<CoreException> {
                service.create(
                    userId = 1L,
                    items = listOf(orderProduct(productId = 10L, price = "120000.00", stock = stock, quantity = 1)),
                    couponApplication = OrderDomainService.CouponApplication(coupon = coupon, userCoupon = userCoupon),
                    now = now,
                )
            }

            // assert
            assertAll(
                { assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
                { assertThat(stock.quantity).isEqualTo(10) },
                { assertThat(userCoupon.status).isEqualTo(UserCouponStatus.AVAILABLE) },
            )
        }
    }

    private fun orderProduct(
        productId: Long,
        price: String,
        stock: ProductStockModel = ProductStockModel(productId = productId, quantity = 10),
        quantity: Int,
    ): OrderDomainService.OrderProduct {
        return OrderDomainService.OrderProduct(
            product = ProductModel(
                brandId = 1L,
                name = "Product$productId",
                description = "Product",
                price = BigDecimal(price),
            ).withId(productId),
            stock = stock,
            quantity = quantity,
        )
    }

    private fun coupon(
        discountValue: String,
        minOrderAmount: BigDecimal? = null,
        expiredAt: ZonedDateTime,
    ): CouponModel {
        return CouponModel(
            name = "쿠폰",
            type = CouponType.FIXED,
            discountValue = BigDecimal(discountValue),
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        ).withId(100L)
    }
}
