package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class InMemoryUserCouponRepository : UserCouponRepository {
    private val data: MutableMap<Long, UserCouponModel> = HashMap()
    private var sequence = 1L

    override fun save(userCouponModel: UserCouponModel): UserCouponModel {
        val id = sequence++
        try {
            val idField = BaseEntity::class.java.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(userCouponModel, id)
            idField.isAccessible = false
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        data[id] = userCouponModel
        return userCouponModel
    }

    override fun countAllByCouponId(couponId: Long): Long {
        TODO("Not yet implemented")
    }

    override fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<UserCouponModel> =
        data.values.filter { it.coupon.id == couponId }
            .let { PageImpl(it, pageable, it.size.toLong()) }

    override fun findByUserId(userId: Long): List<UserCouponModel> =
        data.values.filter { it.userId == userId }

    override fun findWithLockById(id: Long): UserCouponModel? = data[id]

    override fun existsByCouponIdAndUserId(couponId: Long, userId: Long): Boolean =
        data.values.any { it.coupon.id == couponId && it.userId == userId }
}
