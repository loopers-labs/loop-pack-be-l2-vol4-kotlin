package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssue
import com.loopers.domain.coupon.CouponIssueRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class CouponIssueRepositoryImpl(
    private val couponIssueJpaRepository: CouponIssueJpaRepository,
) : CouponIssueRepository {
    override fun save(issue: CouponIssue): CouponIssue {
        if (issue.id > 0L) {
            val entity = couponIssueJpaRepository.findByIdOrNull(issue.id)
                ?: throw CoreException(ErrorType.NOT_FOUND, "Coupon issue not found.")

            entity.update(issue)
            return couponIssueJpaRepository.save(entity)
                .let(CouponIssueMapper::toDomain)
        }

        return CouponIssueMapper.toEntity(issue)
            .let(couponIssueJpaRepository::save)
            .let(CouponIssueMapper::toDomain)
    }

    override fun findById(issueId: Long): CouponIssue? {
        return couponIssueJpaRepository.findByIdOrNull(issueId)
            ?.let(CouponIssueMapper::toDomain)
    }

    override fun findAllByMemberId(memberId: Long): List<CouponIssue> {
        return couponIssueJpaRepository.findAllByMemberIdOrderByCreatedAtDescIdDesc(memberId)
            .map(CouponIssueMapper::toDomain)
    }

    override fun findAllByCouponId(couponId: Long, page: Int, size: Int): Page<CouponIssue> {
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id"),
            ),
        )
        return couponIssueJpaRepository.findAllByCouponId(couponId, pageable)
            .map(CouponIssueMapper::toDomain)
    }

    override fun existsByCouponId(couponId: Long): Boolean {
        return couponIssueJpaRepository.existsByCouponId(couponId)
    }
}
