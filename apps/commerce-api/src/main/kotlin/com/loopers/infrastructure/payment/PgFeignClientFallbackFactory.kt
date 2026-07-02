package com.loopers.infrastructure.payment

import org.slf4j.LoggerFactory
import org.springframework.cloud.openfeign.FallbackFactory
import org.springframework.stereotype.Component

// 서킷 OPEN / 타임아웃 / 5xx 시 호출됨. 결과 불명을 의미하는 빈 응답을 돌려준다.
// 어댑터(PgClientAdapter)가 빈 응답을 PgPaymentResult.unknown()(=PENDING) 으로 해석한다.
@Component
class PgFeignClientFallbackFactory : FallbackFactory<PgFeignClient> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun create(cause: Throwable): PgFeignClient {
        log.warn("PG 호출 fallback: {}", cause.message)
        return object : PgFeignClient {
            override fun requestPayment(userId: String, request: PgPaymentRequest) =
                PgApiResponse<PgTransactionResponse>(meta = null, data = null)

            override fun getByTransactionKey(userId: String, transactionKey: String) =
                PgApiResponse<PgTransactionResponse>(meta = null, data = null)

            override fun findByOrderId(userId: String, orderId: String) =
                PgApiResponse<PgOrderTransactionsResponse>(
                    meta = PgApiResponse.Meta(result = "FALLBACK", errorCode = null, message = null),
                    data = null,
                )
        }
    }
}
