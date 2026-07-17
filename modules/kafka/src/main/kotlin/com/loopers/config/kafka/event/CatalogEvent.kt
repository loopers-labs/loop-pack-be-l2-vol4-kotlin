package com.loopers.config.kafka.event

import java.time.Instant

/**
 * 상품 도메인 이벤트 메시지.
 * catalog-events 토픽으로 발행되며, Consumer가 product_metrics를 갱신하는 데 사용한다.
 *
 * @property eventId Outbox에서 부여하는 고유 ID (멱등 처리 키)
 * @property eventType 이벤트 종류 (PRODUCT_VIEWED, PRODUCT_LIKED, PRODUCT_UNLIKED)
 * @property productId 대상 상품 ID (파티션 키로도 사용)
 * @property userId 행위 주체 사용자 ID (비로그인 조회 시 null)
 * @property payload 추가 데이터 (확장용)
 * @property occurredAt 이벤트 발생 시각
 * @property version 이벤트 스키마 버전
 */
data class CatalogEvent(
    val eventId: String,
    val eventType: String,
    val productId: Long,
    val userId: Long?,
    val payload: Map<String, Any?> = emptyMap(),
    val occurredAt: Instant = Instant.now(),
    val version: Long = 1,
)
