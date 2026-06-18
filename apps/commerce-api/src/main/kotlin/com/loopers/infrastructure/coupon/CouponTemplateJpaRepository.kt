package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

interface CouponTemplateJpaRepository : JpaRepository<CouponTemplateEntity, Long> {
    /**
     * 아직 삭제되지 않은(deleted_at IS NULL) 템플릿을 소프트 삭제하고, 영향받은 row 수를 반환한다.
     * - 반환값 1: 정상 삭제됨
     * - 반환값 0: 해당 id의 미삭제 템플릿이 존재하지 않음(미존재 또는 이미 삭제됨)
     */
    @Transactional
    @Modifying
    @Query(
        """
        UPDATE CouponTemplateEntity c
        SET c.deletedAt = :now
        WHERE c.id = :id AND c.deletedAt IS NULL
        """,
    )
    fun softDeleteById(@Param("id") id: Long, @Param("now") now: ZonedDateTime): Int
}
