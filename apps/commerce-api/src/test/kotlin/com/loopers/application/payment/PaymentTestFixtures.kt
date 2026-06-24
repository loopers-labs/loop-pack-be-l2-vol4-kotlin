package com.loopers.application.payment

import com.loopers.application.coupon.IssueCouponCommand
import com.loopers.application.coupon.usecase.IssueCouponUsecase
import com.loopers.application.order.OrderCommand
import com.loopers.application.order.usecase.CreateOrderUsecase
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserService
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

data class PendingOrderContext(
    val loginId: String,
    val password: String,
    val orderId: Long,
    val userId: Long,
    val productId: Long,
    val quantity: Int,
    val paidPrice: BigDecimal,
    val userCouponId: Long? = null,
)

@Component
class PaymentTestFixtures(
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val createOrderUsecase: CreateOrderUsecase,
    private val orderRepository: OrderRepository,
    private val couponRepository: CouponRepository,
    private val issueCouponUsecase: IssueCouponUsecase,
    private val transactionTemplate: TransactionTemplate,
) {
    private val password = "Password1!"
    private val quantity = 1
    private val productPrice = BigDecimal("10000")

    fun pendingOrder(): PendingOrderContext = createPendingOrder(issueCoupon = false)

    fun pendingOrderWithCoupon(): PendingOrderContext = createPendingOrder(issueCoupon = true)

    private fun createPendingOrder(issueCoupon: Boolean): PendingOrderContext {
        // UserModel 은 loginId 에 영숫자만 허용(^[A-Za-z0-9]+$) → UUID 하이픈 제거
        val loginId = "paymenttester" + UUID.randomUUID().toString().replace("-", "").take(8)
        val user = userService.signUp(
            UserService.SignUpCommand(
                loginId = loginId,
                password = password,
                name = "결제테스터",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "$loginId@loopers.com",
            ),
        )
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "결제상품", description = "설명", price = productPrice),
        )
        productStockRepository.save(ProductStockModel(productId = product.id, quantity = 10))

        val appliedCouponId = if (issueCoupon) {
            val coupon = couponRepository.save(
                CouponModel(
                    name = "결제쿠폰",
                    type = CouponType.FIXED,
                    discountValue = BigDecimal("3000.00"),
                    minOrderAmount = null,
                    expiredAt = ZonedDateTime.now().plusDays(30),
                ),
            )
            issueCouponUsecase.execute(IssueCouponCommand(loginId = loginId, password = password, couponId = coupon.id)).id
        } else {
            null
        }

        val order = createOrderUsecase.execute(
            OrderCommand(
                loginId = loginId,
                password = password,
                items = listOf(OrderCommand.OrderItemCommand(productId = product.id, quantity = quantity)),
                couponId = appliedCouponId,
            ),
        )

        return PendingOrderContext(
            loginId = loginId,
            password = password,
            orderId = order.id,
            userId = user.id,
            productId = product.id,
            quantity = quantity,
            paidPrice = order.paidPrice,
            userCouponId = orderRepository.findById(order.id)?.userCouponId,
        )
    }

    fun paidOrder(): PendingOrderContext {
        val ctx = pendingOrder()
        transactionTemplate.execute {
            val order = orderRepository.findById(ctx.orderId)!!
            order.markAsPaid()
        }
        return ctx
    }
}
