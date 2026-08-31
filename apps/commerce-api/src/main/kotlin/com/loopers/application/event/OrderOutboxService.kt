package com.loopers.application.event
import com.loopers.domain.event.OutboxEvent;import com.loopers.domain.order.Order;import com.loopers.infrastructure.event.OutboxJpaRepository;import com.loopers.infrastructure.order.OrderJpaRepository;import org.springframework.stereotype.Component;import org.springframework.transaction.annotation.Transactional
// Hides: the atomic order/outbox local transaction.
@Component class OrderOutboxService(private val orders:OrderJpaRepository,private val outbox:OutboxJpaRepository){@Transactional fun create(eventId:String,buyer:Long,amount:Long,fail:Boolean):OutboxEvent{val o=orders.save(Order(buyer,amount));val e=outbox.save(OutboxEvent(eventId,o.id.toString(),"order-confirmed:${o.id}"));if(fail)error("forced rollback");return e}}
