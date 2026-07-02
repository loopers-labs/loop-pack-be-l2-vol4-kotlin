package com.loopers.projection.like.infrastructure.persistence

import com.loopers.projection.like.port.ProcessedKafkaEventRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ProcessedKafkaEventRepositoryImpl(
    private val processedKafkaEventJpaRepository: ProcessedKafkaEventJpaRepository,
) : ProcessedKafkaEventRepository {
    override fun recordIfAbsent(
        eventId: UUID,
        consumerGroup: String,
        eventType: String,
    ): Boolean {
        val id = ProcessedKafkaEventJpaId(eventId = eventId, consumerGroup = consumerGroup)
        if (processedKafkaEventJpaRepository.existsById(id)) {
            return false
        }

        return try {
            processedKafkaEventJpaRepository.saveAndFlush(
                ProcessedKafkaEventJpaEntity(
                    id = id,
                    eventType = eventType,
                ),
            )
            true
        } catch (_: DataIntegrityViolationException) {
            false
        }
    }
}
