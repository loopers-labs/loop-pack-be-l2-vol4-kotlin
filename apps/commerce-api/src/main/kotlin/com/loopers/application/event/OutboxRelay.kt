package com.loopers.application.event
import com.loopers.domain.event.*;import com.loopers.infrastructure.event.OutboxJpaRepository;import org.springframework.stereotype.Component;import org.springframework.transaction.annotation.Transactional
// Hides: single-row relay locking, publish-before-status ordering, and crash seam.
@Component class OutboxRelay(private val repo:OutboxJpaRepository,private val publisher:OutboxPublisher){@Transactional fun relay(id:Long,crashAfterAck:Boolean){val e=repo.findLocked(id)?:error("missing");if(e.status==OutboxEvent.Status.PUBLISHED)return;publisher.publish(e.eventId,e.payload);if(crashAfterAck)throw CrashAfterPublish();e.published()};class CrashAfterPublish:RuntimeException()}
