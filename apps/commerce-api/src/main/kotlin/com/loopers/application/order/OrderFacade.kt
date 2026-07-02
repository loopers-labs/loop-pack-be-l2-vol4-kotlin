package com.loopers.application.order

import com.loopers.domain.brand.BrandService
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.IssuedCouponService
import com.loopers.domain.order.OrderCreationService
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.product.ProductService
import com.loopers.infrastructure.product.ProductCacheManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val productService: ProductService,
    private val brandService: BrandService,
    private val orderCreationService: OrderCreationService,
    private val paymentService: PaymentService,
    private val issuedCouponService: IssuedCouponService,
    private val couponTemplateService: CouponTemplateService,
    private val productCacheManager: ProductCacheManager,
) {

    @Transactional
    fun createOrder(userId: Long, items: List<OrderItemCommand>, couponId: Long?): OrderDetailInfo {
        val products = items.map { productService.getProductWithLock(it.productId) }
        val brands = brandService.getBrandsByIds(products.map { it.brandId }.distinct())

        val order = orderCreationService.buildOrder(
            userId = userId,
            products = products,
            brands = brands,
            quantities = items.map { it.quantity },
        )

        if (couponId != null) {
            val issuedCoupon = issuedCouponService.getById(couponId)
            issuedCoupon.validateOwner(userId)

            val template = couponTemplateService.getById(issuedCoupon.couponTemplateId)
            val originalPrice = order.calculateTotalPrice()
            template.validateUsable(originalPrice)

            val discountAmount = template.calculateDiscount(originalPrice)
            issuedCoupon.use()

            order.applyCoupon(couponId = issuedCoupon.id, discountAmount = discountAmount)
        }

        val savedOrder = orderService.createOrder(order)
        paymentService.createPayment(orderId = savedOrder.id, amount = savedOrder.totalPrice)

        items.forEach { productCacheManager.evictDetail(it.productId) }

        return OrderDetailInfo.from(savedOrder)
    }
}
