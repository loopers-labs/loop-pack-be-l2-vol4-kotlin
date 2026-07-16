package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.shared.event.ConsumedEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory

object ConsumedEventDeserializer {
    private val logger = LoggerFactory.getLogger(ConsumedEventDeserializer::class.java)

    private val objectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true)
        .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)

    fun <T : ConsumedEvent> read(record: ConsumerRecord<String, ByteArray>, type: Class<T>): T? {
        val event = try {
            objectMapper.readValue(record.value(), type)
        } catch (e: InvalidTypeIdException) {
            logger.warn("알 수 없는 eventType — skip (topic={}, offset={}, type={})", record.topic(), record.offset(), e.typeId)
            return null
        } catch (e: Exception) {
            logger.warn("역직렬화 불가 메시지 — skip (topic={}, offset={}): {}", record.topic(), record.offset(), e.javaClass.simpleName)
            return null
        }
        if (event.eventId.isBlank()) {
            logger.warn("eventId 없는 메시지 — skip (topic={}, offset={})", record.topic(), record.offset())
            return null
        }
        return event
    }
}
