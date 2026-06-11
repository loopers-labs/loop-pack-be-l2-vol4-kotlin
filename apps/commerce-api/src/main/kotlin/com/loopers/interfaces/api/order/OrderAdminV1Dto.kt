package com.loopers.interfaces.api.order

import com.loopers.domain.order.AdminOrderDetail
import com.loopers.domain.order.AdminOrderSummary
import java.time.ZonedDateTime

class OrderAdminV1Dto {
    data class AdminOrderSummaryResponse(
        val id: Long,
        val userId: Long,
        val loginId: String,
        val status: String,
        val totalAmount: Long,
        val orderedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(summary: AdminOrderSummary): AdminOrderSummaryResponse = AdminOrderSummaryResponse(
                id = summary.id,
                userId = summary.userId,
                loginId = summary.loginId,
                status = summary.status.name,
                totalAmount = summary.totalAmount,
                orderedAt = summary.orderedAt,
            )
        }
    }

    data class AdminOrderDetailResponse(
        val id: Long,
        val userId: Long,
        val loginId: String,
        val status: String,
        val totalAmount: Long,
        val usedPoint: Long,
        val actualAmount: Long,
        val earnPoint: Long,
        val orderedAt: ZonedDateTime,
        val items: List<OrderV1Dto.OrderItemResponse>,
    ) {
        companion object {
            fun from(detail: AdminOrderDetail): AdminOrderDetailResponse = AdminOrderDetailResponse(
                id = detail.id,
                userId = detail.userId,
                loginId = detail.loginId,
                status = detail.status.name,
                totalAmount = detail.totalAmount,
                usedPoint = detail.usedPoint,
                actualAmount = detail.actualAmount,
                earnPoint = detail.earnPoint,
                orderedAt = detail.orderedAt,
                items = detail.items.map { OrderV1Dto.OrderItemResponse.from(it) },
            )
        }
    }
}
