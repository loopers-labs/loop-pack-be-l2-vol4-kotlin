package com.loopers.domain.like.infrastructure.persistence

import com.loopers.domain.like.port.LikeBulkRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * 읽기 최적화(인덱스/캐시) 실습용 좋아요 분포 데이터를 적재하는 도메인 bulk repository.
 * 분포가 product_id 의 결정적 함수라 DB-side INSERT ... SELECT 로 생성·적재한다(JVM 객체 미생성).
 */
@Component
class LikeBulkRepositoryImpl(
    private val jdbcTemplate: JdbcTemplate,
) : LikeBulkRepository {
    // 상품별 좋아요 0~(max-1)개 분포. user_id = 1..k (상품 내 PK(product_id, user_id) 충돌 없음).
    override fun seedLikesByDistribution(maxLikesPerProduct: Int): Int =
        jdbcTemplate.update(SEED_LIKES_SQL, maxLikesPerProduct, maxLikesPerProduct)

    // likes 에서 집계 파생 → like_count == COUNT(likes), 좋아요 0 상품도 count 0 행 생성.
    override fun deriveLikeCounts(): Int =
        jdbcTemplate.update(DERIVE_COUNTS_SQL)

    override fun countLikes(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM likes", Long::class.java) ?: 0L

    override fun countLikeCounts(): Long =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_like_counts", Long::class.java) ?: 0L

    companion object {
        // 재귀 깊이 = maxLikesPerProduct(≤1000) 라 기본 cte_max_recursion_depth(1000) 내에서 동작.
        private const val SEED_LIKES_SQL =
            """
            INSERT INTO likes (user_id, product_id, created_at)
            WITH RECURSIVE seq(n) AS (
              SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < ?
            )
            SELECT s.n, p.id, NOW()
            FROM products p
            JOIN seq s ON s.n <= MOD(p.id * 7, ?)
            """

        private const val DERIVE_COUNTS_SQL =
            """
            INSERT INTO product_like_counts (product_id, like_count)
            SELECT p.id, COUNT(l.product_id)
            FROM products p
            LEFT JOIN likes l ON l.product_id = p.id
            GROUP BY p.id
            """
    }
}
