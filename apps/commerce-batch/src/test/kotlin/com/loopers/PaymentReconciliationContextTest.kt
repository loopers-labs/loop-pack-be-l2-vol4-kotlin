package com.loopers

import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PgClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [CommerceBatchApplication::class])
@TestPropertySource(properties = ["spring.batch.job.enabled=false"])
class PaymentReconciliationContextTest
    @Autowired
    constructor(
        private val paymentRepository: PaymentRepository,
        private val pgClient: PgClient,
    ) {
        @Test
        fun `batch 컨텍스트가 결제 도메인 빈을 주입받는다`() {
            assertThat(paymentRepository).isNotNull
            assertThat(pgClient).isNotNull
        }
    }
