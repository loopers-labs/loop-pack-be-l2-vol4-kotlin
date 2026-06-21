package com.loopers.infrastructure.cache

import java.time.Duration
import kotlin.random.Random

/**
 * 캐시 키 / ZSET 키 / TTL / 개수 상한 상수.
 *
 * ZSET member 는 데이터 키 문자열 그 자체로 둔다. 이렇게 하면 eviction 시 ZSET 에서 꺼낸
 * member 를 그대로 DEL 하면 되므로 키 재구성이 필요 없다.
 */
object CacheKeys {
    /** 상품 단건 캐시: product:{id} */
    fun product(id: Long): String = "product:$id"

    /** 브랜드 단건 캐시: brand:{id} */
    fun brand(id: Long): String = "brand:$id"

    /** 브랜드 좋아요순 페이지 캐시: brand:{brandId}:like_desc:{page}:{size} */
    fun likeDescPage(brandId: Long, page: Int, size: Int): String = "brand:$brandId:like_desc:$page:$size"

    const val PRODUCT_ZSET = "cache:zset:product"
    const val BRAND_ZSET = "cache:zset:brand"
    const val PAGE_ZSET = "cache:zset:brand:like_desc"

    /** 전체 20만개 중 상위 20% ≈ 4만개 */
    const val PRODUCT_LIMIT = 40_000L

    /** 브랜드는 수가 적어 사실상 전부 유지 */
    const val BRAND_LIMIT = 10_000L

    /** 자주 조회되는 페이지만 유지 */
    const val PAGE_LIMIT = 300L

    private val PRODUCT_TTL: Duration = Duration.ofHours(1)
    private val BRAND_TTL: Duration = Duration.ofHours(1)
    private val PAGE_TTL: Duration = Duration.ofMinutes(1)

    /** TTL jitter 비율(±10%). 다수의 키가 동시에 만료되어 발생하는 cache stampede 를 방지한다. */
    private const val TTL_JITTER_RATIO = 0.1

    /**
     * 기준 TTL 에 ±[TTL_JITTER_RATIO] 범위의 랜덤 jitter 를 적용한 TTL 을 반환한다.
     * 호출 시점마다 새로 계산되므로, 같은 시점에 적재된 키들도 만료 시점이 분산된다.
     */
    private fun jittered(base: Duration): Duration {
        val baseMillis = base.toMillis()
        val delta = (baseMillis * TTL_JITTER_RATIO).toLong()
        // [-delta, +delta] 범위. delta 가 0 이면 jitter 없이 기준값 그대로.
        val offset = if (delta == 0L) 0L else Random.nextLong(-delta, delta + 1)
        return Duration.ofMillis(baseMillis + offset)
    }

    fun productTtl(): Duration = jittered(PRODUCT_TTL)
    fun brandTtl(): Duration = jittered(BRAND_TTL)
    fun pageTtl(): Duration = jittered(PAGE_TTL)
}
