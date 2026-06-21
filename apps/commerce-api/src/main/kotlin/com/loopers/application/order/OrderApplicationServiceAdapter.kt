package com.loopers.application.order

import com.loopers.domain.auth.AuthService
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.order.AdminOrderDetail
import com.loopers.domain.order.AdminOrderSummary
import com.loopers.domain.order.OrderDetail
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderSummary
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.order.OrderAdminApplicationServicePort
import com.loopers.interfaces.api.order.OrderApplicationServicePort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Service
class OrderApplicationServiceAdapter(
    private val orderService: OrderService,
    private val userService: UserService,
    private val authService: AuthService,
    private val orderPlacement: OrderPlacement,
) : OrderApplicationServicePort,
    OrderAdminApplicationServicePort {

    /**
     * 주문 생성만 수행한다: 재고/쿠폰을 선점하고 PAYMENT_PENDING 주문을 확정한다.
     * 실제 결제는 별도 결제 API 의 책임이며, 결제 완료 시 PAYMENT_PENDING → PAYMENT_COMPLETED 로 전이된다.
     */
    override fun createOrder(command: CreateOrderCommand): OrderDetail {
        // 쿠폰 낙관적 락 충돌은 place 의 트랜잭션 커밋(flush) 시점에 발생하므로 트랜잭션 경계 밖인 여기서 잡는다.
        val pending = try {
            orderPlacement.place(command)
        } catch (e: OptimisticLockingFailureException) {
            throw CoreException(ErrorType.CONFLICT, "다른 주문에서 이미 사용된 쿠폰입니다. 다시 시도해 주세요.")
        }
        return OrderDetail.from(pending)
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
