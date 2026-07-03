package com.loopers.support.outbox.event

data class Route(
    val topicName: String,
    val key: String,
)
