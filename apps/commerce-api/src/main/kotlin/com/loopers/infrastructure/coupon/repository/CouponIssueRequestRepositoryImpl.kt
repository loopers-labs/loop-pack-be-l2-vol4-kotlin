package com.loopers.infrastructure.coupon.repository

import com.loopers.domain.coupon.model.CouponIssueRequest
import com.loopers.domain.coupon.repository.CouponIssueRequestRepository
import com.loopers.infrastructure.coupon.mapper.CouponIssueRequestMapper
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestRepositoryImpl(
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository,
) : CouponIssueRequestRepository {
    override fun save(request: CouponIssueRequest): CouponIssueRequest {
        return try {
            val entity = if (request.id == 0L) {
                CouponIssueRequestMapper.toEntity(request)
            } else {
                couponIssueRequestJpaRepository.findByIdOrNull(request.id)
                    ?.also { it.update(request) }
                    ?: throw CoreException(ErrorType.NOT_FOUND, "Coupon issue request not found.")
            }

            couponIssueRequestJpaRepository.save(entity)
                .let(CouponIssueRequestMapper::toDomain)
        } catch (e: DataIntegrityViolationException) {
            throw CoreException(ErrorType.CONFLICT, "Coupon issue request already exists.")
        }
    }

    override fun findByRequestId(requestId: String): CouponIssueRequest? {
        return couponIssueRequestJpaRepository.findByRequestId(requestId)
            ?.let(CouponIssueRequestMapper::toDomain)
    }

    override fun findByRequestIdForUpdate(requestId: String): CouponIssueRequest? {
        return couponIssueRequestJpaRepository.findByRequestIdForUpdate(requestId)
            ?.let(CouponIssueRequestMapper::toDomain)
    }
}
