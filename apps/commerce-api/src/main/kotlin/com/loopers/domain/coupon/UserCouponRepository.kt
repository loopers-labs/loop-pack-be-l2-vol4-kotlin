package com.loopers.domain.coupon

import java.time.LocalDateTime

interface UserCouponRepository {
    fun save(userCoupon: UserCoupon): UserCoupon

    fun saveIssued(userCoupon: UserCoupon): UserCoupon

    fun findById(id: Long): UserCoupon?

    /**
     * (userId 가 소유한 발급분 && 미사용 상태) 일 때만 used_at 을 [usedAt] 으로 원자적으로 갱신한다.
     *
     * - 반환값 true: 사용 처리 성공.
     * - 반환값 false: 다음 중 하나의 경우. 호출자는 정확한 사유를 구분하지 않는다.
     *   - 존재하지 않는 발급분
     *   - 다른 유저의 발급분 (소유자 검증 실패)
     *   - 이미 사용된 발급분
     *
     * WHERE 절에 user_id 조건을 함께 포함하여, Facade 의 소유자 검증과는 별개로
     * DB 레벨에서도 "본인 소유 + 미사용" 두 불변식을 동시에 강제한다 (defense-in-depth).
     */
    fun useIfNotUsed(id: Long, userId: Long, usedAt: LocalDateTime): Boolean

    /**
     * (userId 가 소유한 발급분 && 사용 상태) 일 때만 used_at 을 null 로 원자적으로 갱신한다.
     *
     * - 반환값 true: 사용 취소 성공.
     * - 반환값 false: 존재하지 않거나 / 다른 유저 소유 / 이미 미사용.
     *
     * useIfNotUsed 와 동일한 이유로 user_id 조건을 함께 포함한다.
     */
    fun cancelUseIfUsed(id: Long, userId: Long): Boolean
}
