package com.loopers.domain.payment.integration

import com.loopers.domain.payment.application.service.PaymentService
import com.loopers.domain.payment.application.service.PaymentTransitionResult
import com.loopers.domain.payment.exception.InvalidPaymentException
import com.loopers.domain.payment.infrastructure.persistence.OutboxEventJpaRepository
import com.loopers.domain.payment.model.PaymentModel
import com.loopers.domain.payment.model.PaymentStatus
import com.loopers.domain.payment.port.PaymentRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class PaymentServiceIntegrationTest
    @Autowired
    constructor(
        private val paymentService: PaymentService,
        private val paymentRepository: PaymentRepository,
        private val outboxEventJpaRepository: OutboxEventJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `동일_거래키에_승인과_실패가_동시에_들어와도_하나의_완료상태만_반영된다`() {
            val transactionKey = "20260625:TR:test01"
            paymentRepository.save(
                PaymentModel.request(orderId = 1L)
                    .assignTransactionKey(transactionKey),
            )

            val results = 동시에_결제상태_전이(
                listOf(
                    Callable { paymentService.approveByTransactionKey(transactionKey) },
                    Callable { paymentService.failByTransactionKey(transactionKey, "한도초과") },
                ),
            )

            val finalPayment = paymentRepository.findByExternalTransactionKeyOrNull(transactionKey)
            val successCount = results.count {
                it.isSuccess && (it.getOrThrow() as? PaymentTransitionResult)?.changed == true
            }
            val invalidTransitionCount = results.count { it.exceptionOrNull() is InvalidPaymentException }

            assertThat(successCount).isEqualTo(1)
            assertThat(invalidTransitionCount).isEqualTo(1)
            assertThat(finalPayment?.status).isIn(PaymentStatus.APPROVED, PaymentStatus.FAILED)
            assertThat(outboxEventJpaRepository.count()).isEqualTo(1)
        }

        @Test
        fun `동일_주문에_거래키_할당이_동시에_들어와도_하나의_거래키만_반영된다`() {
            val orderId = 1L
            paymentRepository.save(PaymentModel.request(orderId = orderId))

            val results = 동시에_결제상태_전이(
                listOf(
                    Callable { paymentService.assignTransactionKey(orderId, "20260625:TR:test01") },
                    Callable { paymentService.assignTransactionKey(orderId, "20260625:TR:test02") },
                ),
            )

            val finalPayment = paymentRepository.findByOrderIdOrNull(orderId)
            val successCount = results.count { it.isSuccess }
            val invalidTransitionCount = results.count { it.exceptionOrNull() is InvalidPaymentException }

            assertThat(successCount).isEqualTo(1)
            assertThat(invalidTransitionCount).isEqualTo(1)
            assertThat(finalPayment?.externalTransactionKey).isIn("20260625:TR:test01", "20260625:TR:test02")
            assertThat(outboxEventJpaRepository.count()).isZero()
        }

        private fun <T> 동시에_결제상태_전이(
            commands: List<Callable<T>>,
        ): List<Result<T>> {
            val executor = Executors.newFixedThreadPool(commands.size)
            val ready = CountDownLatch(commands.size)
            val start = CountDownLatch(1)
            val results = CopyOnWriteArrayList<Result<T>>()

            try {
                val futures = commands.map { command ->
                    executor.submit {
                        ready.countDown()
                        start.await()
                        results.add(runCatching { command.call() })
                    }
                }

                assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue()
                start.countDown()
                futures.forEach { it.get(5, TimeUnit.SECONDS) }
                return results.toList()
            } finally {
                executor.shutdownNow()
            }
        }
    }
