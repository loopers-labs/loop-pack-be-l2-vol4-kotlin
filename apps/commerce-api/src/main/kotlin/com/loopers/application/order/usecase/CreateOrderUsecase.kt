package com.loopers.application.order.usecase

import com.loopers.application.order.OrderCommand
import com.loopers.application.order.OrderInfo
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.order.OrderDomainService
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class CreateOrderUsecase(
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val orderRepository: OrderRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val orderDomainService = OrderDomainService()

    @Transactional
    fun execute(command: OrderCommand): OrderInfo {
        val user = userService.getProfile(loginId = command.loginId, password = command.password)
        val couponApplication = command.couponId?.let { findCouponApplication(userCouponId = it, userId = user.id) }
        val orderProducts = command.items.sortedBy { it.productId }.map { item ->
            val product = productRepository.findActiveById(item.productId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
            val stock = productStockRepository.findByProductIdForUpdate(item.productId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다.")

            OrderDomainService.OrderProduct(
                product = product,
                stock = stock,
                quantity = item.quantity,
            )
        }

        val saved = orderDomainService.create(
            userId = user.id,
            items = orderProducts,
            couponApplication = couponApplication,
            now = ZonedDateTime.now(),
        ).let { orderRepository.save(it) }

        eventPublisher.publishEvent(
            OrderCreatedEvent(
                orderId = saved.id,
                userId = saved.userId,
                items = saved.items.map { OrderCreatedEvent.Item(productId = it.productId, quantity = it.quantity) },
            ),
        )
        return OrderInfo.from(saved)
    }

    private fun findCouponApplication(userCouponId: Long, userId: Long): OrderDomainService.CouponApplication {
        val userCoupon = userCouponRepository.findByIdAndUserId(id = userCouponId, userId = userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        val coupon = couponRepository.findActiveById(userCoupon.couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        return OrderDomainService.CouponApplication(coupon = coupon, userCoupon = userCoupon)
    }
}
