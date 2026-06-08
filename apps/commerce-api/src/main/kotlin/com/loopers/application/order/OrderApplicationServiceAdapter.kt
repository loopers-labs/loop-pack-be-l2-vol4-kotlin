package com.loopers.application.order

import com.loopers.domain.auth.AuthService
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.order.AdminOrderDetail
import com.loopers.domain.order.AdminOrderSummary
import com.loopers.domain.order.OrderDetail
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderSummary
import com.loopers.domain.order.PaymentGateway
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.order.OrderAdminApplicationServicePort
import com.loopers.interfaces.api.order.OrderApplicationServicePort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Service
class OrderApplicationServiceAdapter(
    private val orderService: OrderService,
    private val userService: UserService,
    private val authService: AuthService,
    private val orderPlacement: OrderPlacement,
    private val paymentGateway: PaymentGateway,
) : OrderApplicationServicePort,
    OrderAdminApplicationServicePort {

    /**
     * 트랜잭션 없이 조율한다: 주문 확정(트랜잭션) → 외부 결제(트랜잭션 밖) → 결제 결과 반영(트랜잭션).
     * 외부 결제 호출이 DB 트랜잭션을 점유하지 않도록 경계를 분리한다.
     */
    override fun createOrder(command: CreateOrderCommand): OrderDetail {
        val pending = orderPlacement.place(command)
        val paymentResult = paymentGateway.requestPayment(orderId = pending.id, amount = pending.getActualAmount())
        val finalized = orderPlacement.finalize(orderId = pending.id, paymentResult = paymentResult)
        return OrderDetail.from(finalized)
    }

    @Transactional(readOnly = true)
    override fun getMyOrders(
        userId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
        pageRequest: PageRequest,
    ): PageResult<OrderSummary> {
        if (startAt.isAfter(endAt)) {
            throw CoreException(ErrorType.BAD_REQUEST, "startAt 은 endAt 이후일 수 없습니다.")
        }
        userService.getById(userId)
        val page = orderService.findAllByUserId(userId, startAt, endAt, pageRequest)
        return PageResult.of(
            items = page.items.map { OrderSummary.from(it) },
            pageRequest = pageRequest,
            totalElements = page.totalElements,
        )
    }

    @Transactional(readOnly = true)
    override fun getMyOrder(userId: Long, orderId: Long): OrderDetail {
        val order = orderService.getById(orderId)
        if (order.userId != userId) {
            throw CoreException(ErrorType.FORBIDDEN, "본인의 주문만 조회할 수 있습니다.")
        }
        return OrderDetail.from(order)
    }

    @Transactional(readOnly = true)
    override fun getOrders(pageRequest: PageRequest): PageResult<AdminOrderSummary> {
        val page = orderService.findAll(pageRequest)
        val userIds = page.items.map { it.userId }.distinct()
        val loginIds = authService.findLoginIdsByUserIds(userIds)
        val summaries = page.items.map { order ->
            AdminOrderSummary.of(order, loginIds[order.userId].orEmpty())
        }
        return PageResult.of(items = summaries, pageRequest = pageRequest, totalElements = page.totalElements)
    }

    @Transactional(readOnly = true)
    override fun getOrder(orderId: Long): AdminOrderDetail {
        val order = orderService.getById(orderId)
        val loginId = authService.findLoginIdsByUserIds(listOf(order.userId))[order.userId].orEmpty()
        return AdminOrderDetail.of(order, loginId)
    }
}
