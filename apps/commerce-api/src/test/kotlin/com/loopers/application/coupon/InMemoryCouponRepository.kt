package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.withId
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class InMemoryCouponRepository : CouponRepository {
    private val store = mutableMapOf<Long, CouponModel>()
    private var sequence = 0L

    override fun save(coupon: CouponModel): CouponModel {
        val saved = if (coupon.id == 0L) coupon.withId(++sequence) else coupon
        store[saved.id] = saved
        return saved
    }

    override fun findActiveById(id: Long): CouponModel? {
        return store[id]?.takeIf { it.deletedAt == null }
    }

    override fun findAllActive(pageable: Pageable): Page<CouponModel> {
        val active = store.values.filter { it.deletedAt == null }.sortedByDescending { it.id }
        val content = active.drop(pageable.pageNumber * pageable.pageSize).take(pageable.pageSize)
        return PageImpl(content, pageable, active.size.toLong())
    }

    override fun findAllByIdIn(ids: List<Long>): List<CouponModel> {
        return ids.mapNotNull { store[it] }
    }

    override fun claimIssueSlot(couponId: Long): Boolean {
        return store[couponId]?.takeIf { it.deletedAt == null }?.claimIssueSlot() ?: false
    }
}
