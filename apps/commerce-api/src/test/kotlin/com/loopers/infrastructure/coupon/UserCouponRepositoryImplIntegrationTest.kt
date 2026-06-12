package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UserCouponRepositoryImplIntegrationTest @Autowired constructor(
    private val userCouponRepository: UserCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("발급된 쿠폰 저장 시, ")
    @Nested
    inner class Save {
        @DisplayName("미사용 상태로 저장되며 usedAt 은 null 이다.")
        @Test
        fun save_persistsUnused() {
            // act
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))

            // assert
            val found = userCouponRepository.findById(saved.id!!)
            assertAll(
                { assertThat(found?.userId).isEqualTo(1L) },
                { assertThat(found?.couponId).isEqualTo(10L) },
                { assertThat(found?.usedAt).isNull() },
                { assertThat(found?.isUsed()).isFalse() },
            )
        }
    }
}
