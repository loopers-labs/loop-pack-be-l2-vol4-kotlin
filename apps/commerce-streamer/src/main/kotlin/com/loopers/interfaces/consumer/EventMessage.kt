package com.loopers.interfaces.consumer

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

/**
 * Consumer 측이 독립적으로 정의한 수신 메시지 계약(봉투).
 * Producer 의 클래스를 공유하지 않고 JSON 스키마만 계약으로 삼는다.
 * 관용적 진화를 위해 모르는 필드는 무시한다(tolerant reader).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EventMessage(
    val eventId: String,
    val eventType: String,
    val aggregateId: Long,
    val payload: JsonNode,
)
