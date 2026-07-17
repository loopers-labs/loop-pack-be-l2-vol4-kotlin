package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

/**
 * 선착순 쿠폰 비동기 발급 요청 엔티티.
 * API에서 요청을 생성하고 Kafka에 발행 → Consumer가 처리 후 status를 갱신한다.
 * 사용자는 requestId로 polling하여 결과를 확인할 수 있다.
 */
@Entity
@Table(
    name = "coupon_issue_requests",
    indexes = [
        Index(name = "idx_issue_req_user_coupon", columnList = "user_id, coupon_template_id"),
    ],
)
class CouponIssueRequestModel(
    userId: Long,
    couponTemplateId: Long,
) : BaseEntity() {

    /** 요청 사용자 ID */
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    /** 발급 대상 쿠폰 템플릿 ID */
    @Column(name = "coupon_template_id", nullable = false)
    var couponTemplateId: Long = couponTemplateId
        protected set

    /** 발급 처리 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: CouponIssueRequestStatus = CouponIssueRequestStatus.PENDING
        protected set

    /** 실패 사유 (실패 시에만 존재) */
    @Column(name = "failure_reason")
    var failureReason: String? = null
        protected set

    /** 발급 성공으로 상태 전이 */
    fun markSuccess() {
        status = CouponIssueRequestStatus.SUCCESS
    }

    /** 발급 실패로 상태 전이 */
    fun markFailed(reason: String) {
        status = CouponIssueRequestStatus.FAILED
        failureReason = reason
    }
}

/**
 * 쿠폰 발급 요청 상태.
 */
enum class CouponIssueRequestStatus {
    /** 처리 대기 (Kafka에서 소비 전) */
    PENDING,

    /** 발급 성공 */
    SUCCESS,

    /** 발급 실패 (수량 소진, 중복 등) */
    FAILED,
}
