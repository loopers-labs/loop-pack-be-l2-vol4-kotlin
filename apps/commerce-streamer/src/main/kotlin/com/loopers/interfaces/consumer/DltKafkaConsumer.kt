package com.loopers.interfaces.consumer

import com.loopers.config.kafka.KafkaConfig
import com.loopers.failure.application.ConsumedEventFailureRecorder
import com.loopers.failure.infrastructure.ConsumedEventFailure
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.stereotype.Component
import java.nio.ByteBuffer

@Component
class DltKafkaConsumer(
    private val consumedEventFailureRecorder: ConsumedEventFailureRecorder,
) {
    private val logger = LoggerFactory.getLogger(DltKafkaConsumer::class.java)

    @KafkaListener(
        topics = ["product-events-dlt", "order-events-dlt", "user-action-events-dlt"],
        groupId = "commerce-streamer-dlt",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "\${kafka.listener.dlt.auto-startup:\${kafka.listener.auto-startup:true}}",
    )
    fun consumeDlt(messages: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        messages.forEach { record ->
            try {
                consumedEventFailureRecorder.record(toFailure(record))
            } catch (e: Exception) {
                logger.error(
                    "실패 이력 적재 실패 — 수동 확인 필요 (dltTopic={}, offset={})",
                    record.topic(),
                    record.offset(),
                    e,
                )
            }
        }
        acknowledgment.acknowledge()
    }

    private fun toFailure(record: ConsumerRecord<String, ByteArray>): ConsumedEventFailure =
        ConsumedEventFailure(
            originalTopic = headerText(record, KafkaHeaders.DLT_ORIGINAL_TOPIC) ?: record.topic().removeSuffix("-dlt"),
            originalPartition = headerInt(record, KafkaHeaders.DLT_ORIGINAL_PARTITION),
            originalOffset = headerLong(record, KafkaHeaders.DLT_ORIGINAL_OFFSET),
            consumerGroup = headerText(record, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP),
            exceptionFqcn = headerText(record, KafkaHeaders.DLT_EXCEPTION_FQCN),
            exceptionMessage = headerText(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE),
            payload = String(record.value(), Charsets.UTF_8),
        )

    private fun headerText(record: ConsumerRecord<String, ByteArray>, key: String): String? =
        record.headers().lastHeader(key)?.value()?.toString(Charsets.UTF_8)

    private fun headerInt(record: ConsumerRecord<String, ByteArray>, key: String): Int? =
        record.headers().lastHeader(key)?.value()?.takeIf { it.size == Int.SIZE_BYTES }?.let { ByteBuffer.wrap(it).int }

    private fun headerLong(record: ConsumerRecord<String, ByteArray>, key: String): Long? =
        record.headers().lastHeader(key)?.value()?.takeIf { it.size == Long.SIZE_BYTES }?.let { ByteBuffer.wrap(it).long }
}
