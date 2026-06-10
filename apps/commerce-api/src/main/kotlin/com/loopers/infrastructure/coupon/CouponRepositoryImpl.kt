package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun findById(couponId: Long): Coupon? {
        return couponJpaRepository.findByIdOrNull(couponId)
            ?.let(CouponMapper::toDomain)
    }

    override fun findDisplayable(page: Int, size: Int): Page<Coupon> {
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id"),
            ),
        )
        return couponJpaRepository.findAllByIsDeletedFalse(pageable)
            .map(CouponMapper::toDomain)
    }

    override fun save(coupon: Coupon): Coupon {
        try {
            return CouponMapper.toEntity(coupon)
                .let(couponJpaRepository::save)
                .let(CouponMapper::toDomain)
        } catch (e: DataIntegrityViolationException) {
            throw CoreException(ErrorType.CONFLICT, "Coupon already exists.")
        }
    }

    override fun existsByName(name: String): Boolean {
        return couponJpaRepository.existsByName(name)
    }
}
