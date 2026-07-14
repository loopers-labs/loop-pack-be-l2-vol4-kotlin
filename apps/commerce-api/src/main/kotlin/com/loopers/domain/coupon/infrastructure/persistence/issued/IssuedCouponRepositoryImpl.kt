package com.loopers.domain.coupon.infrastructure.persistence.issued

import com.loopers.domain.coupon.exception.DuplicateIssuedCouponException
import com.loopers.domain.coupon.infrastructure.persistence.issued.tableConstraints.IssuedCouponJpaConstraints.ISSUED_COUPON_USER_TEMPLATE_UNIQUE_CONSTRAINT
import com.loopers.domain.coupon.model.IssuedCouponModel
import com.loopers.domain.coupon.port.IssuedCouponRepository
import com.loopers.support.page.PageResult
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class IssuedCouponRepositoryImpl(
    private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
) : IssuedCouponRepository {
    override fun save(issuedCoupon: IssuedCouponModel): IssuedCouponModel =
        try {
            val entity = if (issuedCoupon.id == 0L) {
                IssuedCouponJpaEntity.fromDomain(issuedCoupon)
            } else {
                issuedCouponJpaRepository.findById(issuedCoupon.id).orElseThrow()
                    .also { it.updateFrom(issuedCoupon) }
            }
            issuedCouponJpaRepository.saveAndFlush(entity).toDomain()
        } catch (e: DataIntegrityViolationException) {
            if (e.isUserTemplateUniqueConstraintViolation()) {
                throw DuplicateIssuedCouponException(
                    cause = e,
                )
            }
            throw e
        }

    override fun existsByUserIdAndTemplateId(userId: Long, templateId: Long): Boolean =
        issuedCouponJpaRepository.existsByUserIdAndCouponTemplateId(userId, templateId)

    override fun findByIdOrNull(issuedCouponId: Long): IssuedCouponModel? =
        issuedCouponJpaRepository.findById(issuedCouponId).map { it.toDomain() }.orElse(null)

    override fun findByUserId(userId: Long): List<IssuedCouponModel> =
        issuedCouponJpaRepository.findByUserIdOrderByIssuedAtDesc(userId).map { it.toDomain() }

    override fun findByTemplateId(templateId: Long, page: Int, size: Int): PageResult<IssuedCouponModel> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt"))
        val result = issuedCouponJpaRepository.findByCouponTemplateId(templateId, pageable)
        return PageResult(
            content = result.content.map { it.toDomain() },
            page = page,
            size = size,
            totalElements = result.totalElements,
        )
    }

    private fun DataIntegrityViolationException.isUserTemplateUniqueConstraintViolation(): Boolean =
        generateSequence(this as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .any { it.contains(ISSUED_COUPON_USER_TEMPLATE_UNIQUE_CONSTRAINT, ignoreCase = true) }
}
