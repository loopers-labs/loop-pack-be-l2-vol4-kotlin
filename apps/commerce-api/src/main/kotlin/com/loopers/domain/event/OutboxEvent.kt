package com.loopers.domain.event
import com.loopers.domain.BaseEntity;import jakarta.persistence.*
// Hides: stable event identity and relay publication state.
@Entity @Table(name="outbox_event") class OutboxEvent(val eventId:String,val aggregateId:String,val payload:String):BaseEntity(){enum class Status{PENDING,PUBLISHED};@Enumerated(EnumType.STRING)var status=Status.PENDING;protected set;fun published(){status=Status.PUBLISHED}}
