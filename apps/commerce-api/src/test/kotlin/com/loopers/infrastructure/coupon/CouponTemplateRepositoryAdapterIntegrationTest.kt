package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponTemplateRepositoryPort
import com.loopers.domain.coupon.CouponType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

@SpringBootTest
class CouponTemplateRepositoryAdapterIntegrationTest @Autowired constructor(
    private val couponTemplateRepositoryPort: CouponTemplateRepositoryPort,
    private val couponTemplateJpaRepository: CouponTemplateJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val expiredAt = LocalDateTime.parse("2026-12-31T23:59:59")

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save를 호출할 때, ")
    @Nested
    inner class Save {
        @DisplayName("id가 0인 템플릿을 저장하면, id가 부여되고 DB에 INSERT된다.")
        @Test
        fun insertsTemplate_whenIdIsZero() {
            val saved = couponTemplateRepositoryPort.save(
                CouponTemplate.create(
                    name = "1만원 할인",
                    type = CouponType.FIXED,
                    value = 10_000L,
                    minOrderAmount = 30_000L,
                    expiredAt = expiredAt,
                ),
            )

            assertThat(saved.id).isGreaterThan(0L)
            assertThat(couponTemplateJpaRepository.findById(saved.id)).isPresent
        }

        @DisplayName("저장한 템플릿을 다시 조회하면, 모든 필드가 동일하게 복원된다.")
        @Test
        fun restoresAllFields() {
            val saved = couponTemplateRepositoryPort.save(
                CouponTemplate.create(
                    name = "10% 할인",
                    type = CouponType.RATE,
                    value = 10L,
                    minOrderAmount = 10_000L,
                    expiredAt = expiredAt,
                ),
            )

            val found = couponTemplateRepositoryPort.findById(saved.id)

            assertThat(found).isNotNull
            assertThat(found?.name).isEqualTo("10% 할인")
            assertThat(found?.type).isEqualTo(CouponType.RATE)
            assertThat(found?.value).isEqualTo(10L)
            assertThat(found?.minOrderAmount).isEqualTo(10_000L)
            assertThat(found?.expiredAt).isEqualTo(expiredAt)
        }
    }

    @DisplayName("findById를 호출할 때, ")
    @Nested
    inner class FindById {
        @DisplayName("존재하지 않는 id로 조회하면, null을 반환한다.")
        @Test
        fun returnsNull_whenMissing() {
            assertThat(couponTemplateRepositoryPort.findById(9999L)).isNull()
        }
    }
}
