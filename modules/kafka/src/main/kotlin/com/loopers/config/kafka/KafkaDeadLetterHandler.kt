package com.loopers.config.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition

interface KafkaDeadLetterHandler {
    fun supports(record: ConsumerRecord<*, *>): Boolean

    fun destination(record: ConsumerRecord<*, *>, exception: Exception): TopicPartition

    fun afterPublished(record: ConsumerRecord<*, *>, exception: Exception)
}
