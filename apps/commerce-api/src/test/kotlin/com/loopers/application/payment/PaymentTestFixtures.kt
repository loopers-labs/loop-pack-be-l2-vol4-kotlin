package com.loopers.application.payment

import com.loopers.application.order.OrderCommand
import com.loopers.application.order.usecase.CreateOrderUsecase
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
    private val transactionTemplate: TransactionTemplate,
) {
    private val loginId = "paymenttester"
    private val password = "Password1!"
    private val quantity = 1
    private val productPrice = BigDecimal("10000")

    fun pendingOrder(): PendingOrderContext {
        val user = userService.signUp(
            UserService.SignUpCommand(
                loginId = loginId,
                password = password,
                name = "결제테스터",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "paymenttester@loopers.com",
            ),
        )
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "결제상품", description = "설명", price = productPrice),
        )
        productStockRepository.save(ProductStockModel(productId = product.id, quantity = 10))

        val order = createOrderUsecase.execute(
            OrderCommand(
                loginId = loginId,
                password = password,
                items = listOf(OrderCommand.OrderItemCommand(productId = product.id, quantity = quantity)),
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
