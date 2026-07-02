package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "orders")
class OrderModel(
    userId: Long,
    items: List<OrderItemModel>,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING
        protected set

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    var totalPrice: BigDecimal = BigDecimal.ZERO
        protected set

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    var discountAmount: BigDecimal = BigDecimal.ZERO
        protected set

    @Column(name = "paid_price", nullable = false, precision = 12, scale = 2)
    var paidPrice: BigDecimal = BigDecimal.ZERO
        protected set

    @Column(name = "user_coupon_id")
    var userCouponId: Long? = null
        protected set

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val mutableItems: MutableList<OrderItemModel> = mutableListOf()

    val items: List<OrderItemModel>
        get() = mutableItems.toList()

    init {
        if (userId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "회원 ID는 양수여야 합니다.")
        if (items.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 항목은 비어있을 수 없습니다.")

        items.forEach { addItem(it) }
        totalPrice = mutableItems.fold(BigDecimal.ZERO) { acc, item -> acc + item.subtotal() }
        paidPrice = totalPrice
    }

    fun markAsPaid() {
        if (status != OrderStatus.PENDING) throw CoreException(ErrorType.BAD_REQUEST, "결제 완료로 변경할 수 없는 주문 상태입니다.")
        status = OrderStatus.PAID
    }

    fun markAsFailed() {
        if (status != OrderStatus.PENDING) throw CoreException(ErrorType.BAD_REQUEST, "결제 실패로 변경할 수 없는 주문 상태입니다.")
        status = OrderStatus.FAILED
    }

    fun cancel() {
        if (status != OrderStatus.PENDING) throw CoreException(ErrorType.CONFLICT, "취소할 수 없는 주문 상태입니다.")
        status = OrderStatus.CANCELLED
    }

    fun isPending(): Boolean {
        return status == OrderStatus.PENDING
    }

    fun isPaid(): Boolean {
        return status == OrderStatus.PAID
    }

    fun applyCoupon(userCouponId: Long, discountAmount: BigDecimal) {
        if (status != OrderStatus.PENDING) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰을 적용할 수 없는 주문 상태입니다.")
        if (discountAmount < BigDecimal.ZERO) throw CoreException(ErrorType.BAD_REQUEST, "할인 금액은 음수일 수 없습니다.")
        if (discountAmount > totalPrice) throw CoreException(ErrorType.BAD_REQUEST, "할인 금액은 주문 금액을 초과할 수 없습니다.")
        this.userCouponId = userCouponId
        this.discountAmount = discountAmount
        this.paidPrice = totalPrice - discountAmount
    }

    private fun addItem(item: OrderItemModel) {
        item.assign(this)
        mutableItems.add(item)
    }
}
