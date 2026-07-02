package com.loopers.coupon.infrastructure

import com.loopers.coupon.domain.Coupon
import com.loopers.coupon.domain.CouponRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
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

    override fun findByIdForUpdate(id: Long): Coupon? = couponJpaRepository.findByIdForUpdate(id)
}

interface CouponJpaRepository : JpaRepository<Coupon, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Coupon?
}
