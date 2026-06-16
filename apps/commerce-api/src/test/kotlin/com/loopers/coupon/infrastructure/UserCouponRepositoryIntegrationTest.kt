package com.loopers.coupon.infrastructure

import com.loopers.coupon.domain.UserCoupon
import com.loopers.coupon.domain.UserCouponGrantedType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException

@DataJpaTest
class UserCouponRepositoryIntegrationTest @Autowired constructor(
    private val userCouponJpaRepository: UserCouponJpaRepository,
) {
    @DisplayName("같은 (userId, couponId)를 중복 저장하면, DB unique 제약으로 실패한다.")
    @Test
    fun throwsDataIntegrityViolation_whenUserCouponIsDuplicated() {
        userCouponJpaRepository.saveAndFlush(userCoupon())

        assertThrows<DataIntegrityViolationException> {
            userCouponJpaRepository.saveAndFlush(userCoupon())
        }
    }

    private fun userCoupon(): UserCoupon = UserCoupon(
        userId = 10L,
        couponId = 1L,
        grantedType = UserCouponGrantedType.ADMIN,
        grantedBy = 99L,
    )
}
