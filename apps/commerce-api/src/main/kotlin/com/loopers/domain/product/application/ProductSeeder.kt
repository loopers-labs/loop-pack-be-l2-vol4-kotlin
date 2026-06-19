package com.loopers.domain.product.application

import com.loopers.domain.brand.model.BrandModel
import com.loopers.domain.brand.port.BrandRepository
import com.loopers.domain.brand.vo.BrandName
import com.loopers.domain.product.model.ProductModel
import com.loopers.domain.product.port.ProductBulkRepository
import com.loopers.domain.product.vo.Money
import com.loopers.domain.product.vo.ProductName
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * 읽기 최적화(인덱스/캐시) 실습용 대량 상품 데이터를 생성·적재하는 오케스트레이터.
 * 분포가 있는 더미 데이터를 생성하고, 적재는 도메인 repository 에 위임한다.
 * 로컬 전용이며 [com.loopers.domain.product.presentation.DevSeedController] 를 통해 트리거된다.
 */
@Component
@Profile("local")
class ProductSeeder(
    private val brandRepository: BrandRepository,
    private val productBulkRepository: ProductBulkRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun seedProducts(count: Int, brandCount: Int): SeedResult {
        val existing = productBulkRepository.count()
        if (existing > 0L) {
            log.warn("products 테이블에 이미 {}건이 존재합니다. 시딩을 건너뜁니다.", existing)
            return SeedResult(brandsInserted = 0, productsInserted = 0, elapsedMillis = 0, skipped = true)
        }

        var brandsInserted = 0
        var productsInserted = 0
        val elapsed = measureTimeMillis {
            val brandIds = seedBrands(brandCount)
            brandsInserted = brandIds.size
            val products = generateProducts(count, brandIds)
            productsInserted = productBulkRepository.bulkInsert(products)
        }
        log.info("시딩 완료: brands={}, products={}, {}ms", brandsInserted, productsInserted, elapsed)
        return SeedResult(brandsInserted, productsInserted, elapsed, skipped = false)
    }

    // 브랜드는 소수(기본 100건)라 bulk가 무의미, save를 재사용해 생성 id 를 확보한다.
    private fun seedBrands(brandCount: Int): List<Long> =
        (1..brandCount).map { i ->
            brandRepository.save(BrandModel(name = BrandName.of("brand-$i"))).id
        }

    // 브랜드·가격을 랜덤 분포시킨다 (필터/정렬 실습용)
    private fun generateProducts(count: Int, brandIds: List<Long>): List<ProductModel> =
        (0 until count).map { seq ->
            ProductModel(
                brandId = brandIds[Random.nextInt(brandIds.size)],
                name = ProductName.of("product-$seq"),
                price = Money.of(Random.nextLong(MIN_PRICE, MAX_PRICE + 1)),
            )
        }

    data class SeedResult(
        val brandsInserted: Int,
        val productsInserted: Int,
        val elapsedMillis: Long,
        val skipped: Boolean,
    )

    companion object {
        private const val MIN_PRICE = 1_000L
        private const val MAX_PRICE = 1_000_000_000L
    }
}
