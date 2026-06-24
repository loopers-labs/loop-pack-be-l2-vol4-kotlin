package com.loopers.infrastructure.payment

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.ZonedDateTime

@SpringBootTest
class PaymentRepositoryImplTest @Autowired constructor(
    private val paymentRepository: PaymentRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun pending(orderId: Long) = PaymentModel(
        orderId = orderId,
        userId = 1L,
        amount = BigDecimal(1000),
        cardType = CardType.SAMSUNG,
        cardNo = "1234-5678-9012-3456",
    )

    @Test
    fun `orderId 로 결제를 조회한다`() {
        val saved = paymentRepository.save(pending(100L))
        val found = paymentRepository.findByOrderId(100L)
        assertThat(found?.id).isEqualTo(saved.id)
    }

    @Test
    fun `임계 시각 이전의 PENDING 결제만 조회한다`() {
        paymentRepository.save(pending(101L))
        val stale = paymentRepository.findStalePending(ZonedDateTime.now().plusMinutes(1))
        val fresh = paymentRepository.findStalePending(ZonedDateTime.now().minusMinutes(1))
        assertThat(stale).hasSize(1)
        assertThat(fresh).isEmpty()
    }
}
