package com.loopers.infrastructure.payment

import feign.Request
import org.springframework.context.annotation.Bean
import java.util.concurrent.TimeUnit

/**
 * PG Feign 클라이언트 전용 설정.
 *
 * 의도적으로 [org.springframework.context.annotation.Configuration] 을 붙이지 않는다.
 * 컴포넌트 스캔에 잡히면 모든 Feign 클라이언트에 전역 적용되므로,
 * [PgPaymentClient] 의 `configuration` 속성으로만 주입되어 PG 호출에만 타임아웃을 적용한다.
 *
 * - connectTimeout: 커넥션 수립 최대 대기 시간
 * - readTimeout: 응답 수신 최대 대기 시간 (대부분의 장애는 실패보다 '지연'에서 시작된다)
 */
class PgFeignConfig {
    @Bean
    fun pgFeignOptions(): Request.Options =
        Request.Options(
            CONNECT_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS,
            READ_TIMEOUT_MILLIS,
            TimeUnit.MILLISECONDS,
            true,
        )

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 1_000L

        // 처리 꼬리 latency와 응답 미확정 시 getTransaction 복구를 고려해 성급히 끊지 않는다.
        // 대신 1s 이상은 slow-call 로 카운트해 서킷이 타임아웃보다 먼저 차단한다.
        private const val READ_TIMEOUT_MILLIS = 5_000L
    }
}
