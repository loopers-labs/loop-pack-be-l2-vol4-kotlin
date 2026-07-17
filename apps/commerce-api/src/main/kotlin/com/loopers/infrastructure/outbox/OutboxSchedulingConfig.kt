package com.loopers.infrastructure.outbox

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Outbox 릴레이 스케줄링 활성화 설정.
 */
@EnableScheduling
@Configuration
class OutboxSchedulingConfig
