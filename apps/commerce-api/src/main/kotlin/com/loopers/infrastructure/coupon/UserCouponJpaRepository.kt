package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface UserCouponJpaRepository : JpaRepository<UserCouponJpaEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): UserCouponJpaEntity?

    /**
     * (본인 소유 && used_at is null) 일 때만 [usedAt] 으로 갱신한다.
     * 영향 받은 row 수를 반환한다 (성공=1, 그 외=0).
     *
     * 동시에 여러 트랜잭션이 호출해도 단 한 번만 1을 반환한다 (DB UPDATE 원자성).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update UserCouponJpaEntity uc
        set uc.usedAt = :usedAt
        where uc.id = :id
          and uc.userId = :userId
          and uc.usedAt is null
          and uc.deletedAt is null
        """,
    )
    fun useIfNotUsed(
        @Param("id") id: Long,
        @Param("userId") userId: Long,
        @Param("usedAt") usedAt: LocalDateTime,
    ): Int

    /**
     * (본인 소유 && used_at is not null) 일 때만 used_at 을 null 로 갱신한다.
     * 영향 받은 row 수를 반환한다 (성공=1, 그 외=0).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update UserCouponJpaEntity uc
        set uc.usedAt = null
        where uc.id = :id
          and uc.userId = :userId
          and uc.usedAt is not null
          and uc.deletedAt is null
        """,
    )
    fun cancelUseIfUsed(
        @Param("id") id: Long,
        @Param("userId") userId: Long,
    ): Int
}
