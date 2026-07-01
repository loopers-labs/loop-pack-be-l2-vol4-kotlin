package com.loopers.domain.payment.integration

import com.loopers.domain.payment.infrastructure.persistence.PaymentRecordJpaEntity
import com.loopers.domain.payment.infrastructure.persistence.PaymentRecordJpaRepository
import com.loopers.domain.payment.model.PaymentModel
import com.loopers.domain.payment.model.PaymentStatus
import com.loopers.domain.payment.port.PaymentRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
class PaymentRepositoryIntegrationTest
    @Autowired
    constructor(
        private val paymentRepository: PaymentRepository,
        private val paymentRecordJpaRepository: PaymentRecordJpaRepository,
        private val jdbcTemplate: JdbcTemplate,
        private val databaseCleanUp: DatabaseCleanUp,
        private val transactionTemplate: TransactionTemplate,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `결제상태는_명시_code로_저장된다`() {
            val saved = paymentRepository.save(PaymentModel.request(orderId = 1L))

            val statusCode: Short? = jdbcTemplate.queryForObject(
                "select status from payment_records where payment_record_id = ?",
                Short::class.java,
                saved.id,
            )

            assertThat(statusCode).isEqualTo(PaymentStatus.REQUESTED.code)
        }

        @Test
        fun `결제기록은_도메인으로_복원된다`() {
            val payment = paymentRepository.save(
                PaymentModel.request(orderId = 1L)
                    .assignTransactionKey("20260625:TR:test01"),
            )

            val found = paymentRepository.findByExternalTransactionKeyOrNull("20260625:TR:test01")

            assertThat(found?.id).isEqualTo(payment.id)
            assertThat(found?.orderId).isEqualTo(payment.orderId)
            assertThat(found?.externalTransactionKey).isEqualTo(payment.externalTransactionKey)
            assertThat(found?.status).isEqualTo(payment.status)
        }

        @Test
        fun `외부_거래키로_잠금_조회한_결제기록은_도메인으로_복원된다`() {
            val payment = paymentRepository.save(
                PaymentModel.request(orderId = 1L)
                    .assignTransactionKey("20260625:TR:test01"),
            )

            val found = transactionTemplate.execute {
                paymentRepository.findByExternalTransactionKeyForUpdateOrNull("20260625:TR:test01")
            }

            assertThat(found?.id).isEqualTo(payment.id)
            assertThat(found?.orderId).isEqualTo(payment.orderId)
            assertThat(found?.externalTransactionKey).isEqualTo(payment.externalTransactionKey)
            assertThat(found?.status).isEqualTo(payment.status)
        }

        @Test
        fun `주문_ID로_잠금_조회한_결제기록은_도메인으로_복원된다`() {
            val payment = paymentRepository.save(PaymentModel.request(orderId = 1L))

            val found = transactionTemplate.execute {
                paymentRepository.findByOrderIdForUpdateOrNull(1L)
            }

            assertThat(found?.id).isEqualTo(payment.id)
            assertThat(found?.orderId).isEqualTo(payment.orderId)
            assertThat(found?.status).isEqualTo(payment.status)
        }

        @Test
        fun `주문_ID는_유일해야_한다`() {
            val first = PaymentRecordJpaEntity.fromDomain(PaymentModel.request(orderId = 1L))
            val second = PaymentRecordJpaEntity.fromDomain(PaymentModel.request(orderId = 1L))

            paymentRecordJpaRepository.saveAndFlush(first)

            assertThrows<DataIntegrityViolationException> {
                paymentRecordJpaRepository.saveAndFlush(second)
            }
        }

        @Test
        fun `외부_거래키는_null을_여러_건_허용하지만_값은_유일해야_한다`() {
            paymentRecordJpaRepository.saveAndFlush(PaymentRecordJpaEntity.fromDomain(PaymentModel.request(orderId = 1L)))
            paymentRecordJpaRepository.saveAndFlush(PaymentRecordJpaEntity.fromDomain(PaymentModel.request(orderId = 2L)))
            paymentRecordJpaRepository.saveAndFlush(
                PaymentRecordJpaEntity.fromDomain(
                    PaymentModel.request(orderId = 3L).assignTransactionKey("20260625:TR:test01"),
                ),
            )

            assertThrows<DataIntegrityViolationException> {
                paymentRecordJpaRepository.saveAndFlush(
                    PaymentRecordJpaEntity.fromDomain(
                        PaymentModel.request(orderId = 4L).assignTransactionKey("20260625:TR:test01"),
                    ),
                )
            }
        }
    }
