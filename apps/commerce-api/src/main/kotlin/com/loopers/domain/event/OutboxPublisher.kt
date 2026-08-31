package com.loopers.domain.event
interface OutboxPublisher{fun publish(eventId:String,payload:String)}
