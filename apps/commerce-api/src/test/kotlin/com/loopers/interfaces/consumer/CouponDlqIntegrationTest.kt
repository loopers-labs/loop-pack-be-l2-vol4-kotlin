package com.loopers.interfaces.consumer

import com.loopers.application.coupon.CouponIssueRequestPayload
import com.loopers.config.kafka.KafkaTopics
import com.loopers.domain.coupon.CouponIssueRequestModel
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.utils.DatabaseCleanUp
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import java.time.Duration
import java.util.UUID

@SpringBootTest(properties = ["spring.kafka.properties.auto.offset.reset=earliest"])
@EmbeddedKafka(
    topics = [KafkaTopics.COUPON_ISSUE_REQUESTS, KafkaTopics.COUPON_ISSUE_REQUESTS_DLT],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
class CouponDlqIntegrationTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val embeddedKafkaBroker: EmbeddedKafkaBroker,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("issue 가 계속 실패하면 재시도 소진 후 DLT 토픽으로 발행된다")
    @Test
    fun failedMessageGoesToDlt() {
        // given
        val requestId = UUID.randomUUID().toString()
        val missingCouponId = 999L
        couponIssueRequestRepository.save(
            CouponIssueRequestModel.of(requestId = requestId, couponId = missingCouponId, userId = 1L),
        )

        val dltConsumer = DefaultKafkaConsumerFactory(
            KafkaTestUtils.consumerProps("dlt-test-group", "true", embeddedKafkaBroker),
            StringDeserializer(),
            ByteArrayDeserializer(),
        ).createConsumer()
        dltConsumer.subscribe(listOf(KafkaTopics.COUPON_ISSUE_REQUESTS_DLT))

        // when
        kafkaTemplate.send(
            KafkaTopics.COUPON_ISSUE_REQUESTS,
            missingCouponId.toString(),
            CouponIssueRequestPayload(requestId = requestId, couponId = missingCouponId, userId = 1L),
        )

        // then
        val record = KafkaTestUtils.getSingleRecord(dltConsumer, KafkaTopics.COUPON_ISSUE_REQUESTS_DLT, Duration.ofSeconds(15))
        assertThat(record).isNotNull

        dltConsumer.close()
    }
}
