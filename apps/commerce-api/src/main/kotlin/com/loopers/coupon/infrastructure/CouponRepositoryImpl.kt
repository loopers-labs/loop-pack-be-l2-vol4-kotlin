package com.loopers.coupon.infrastructure

import com.loopers.coupon.domain.Coupon
import com.loopers.coupon.domain.CouponRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
class CouponRepositoryImpl(
    val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun save(coupon: Coupon): Coupon = couponJpaRepository.save(coupon)

    override fun findById(id: Long): Coupon? = couponJpaRepository.findByIdOrNull(id)

    override fun incrementIssuedQuantityIfAvailable(id: Long): Int =
        couponJpaRepository.incrementIssuedQuantityIfAvailable(id)
}

interface CouponJpaRepository : JpaRepository<Coupon, Long> {
    @Modifying
    @Query(
        "update Coupon c set c.issuedQuantity = c.issuedQuantity + 1 " +
            "where c.id = :id and c.issuedQuantity < c.totalQuantity",
    )
    fun incrementIssuedQuantityIfAvailable(@Param("id") id: Long): Int
}
