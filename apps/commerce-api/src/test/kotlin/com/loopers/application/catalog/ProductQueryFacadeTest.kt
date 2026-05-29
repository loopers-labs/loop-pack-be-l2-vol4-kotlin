package com.loopers.application.catalog

import com.loopers.application.catalog.port.CatalogProductQueryPort
import com.loopers.application.catalog.port.LikeProductQueryPort
import com.loopers.application.catalog.port.OrderReservationQueryPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class ProductQueryFacadeTest {
    @DisplayName("상품 목록은 Catalog row 에 Like 상태를 보강하고 실제 재고 기준 soldOut 을 계산한다.")
    @Test
    fun listProductsComposesLikedByMeAndSoldOut() {
        val facade = ProductQueryFacade(
            catalogProductQueryPort = FakeCatalogProductQueryPort(),
            likeProductQueryPort = FakeLikeProductQueryPort(likedProductIds = setOf(1L)),
            orderReservationQueryPort = FakeOrderReservationQueryPort(activeReservedQuantity = 0),
        )

        val result = facade.getProducts(sort = ProductSort.LATEST, page = 0, size = 20, userId = 7L)

        assertAll(
            { assertThat(result).hasSize(2) },
            { assertThat(result[0].productId).isEqualTo(1L) },
            { assertThat(result[0].likedByMe).isTrue() },
            { assertThat(result[0].soldOut).isFalse() },
            { assertThat(result[1].productId).isEqualTo(2L) },
            { assertThat(result[1].likedByMe).isFalse() },
            { assertThat(result[1].soldOut).isTrue() },
        )
    }

    @DisplayName("상품 상세는 Order 활성 예약 수량을 반영해 soldOut 을 계산한다.")
    @Test
    fun getProductDetailComposesReservedQuantityForSoldOut() {
        val facade = ProductQueryFacade(
            catalogProductQueryPort = FakeCatalogProductQueryPort(),
            likeProductQueryPort = FakeLikeProductQueryPort(likedProductIds = emptySet()),
            orderReservationQueryPort = FakeOrderReservationQueryPort(activeReservedQuantity = 5),
        )

        val result = facade.getProductDetail(productId = 1L, userId = null)

        assertAll(
            { assertThat(result.product.productId).isEqualTo(1L) },
            { assertThat(result.product.likedByMe).isFalse() },
            { assertThat(result.product.soldOut).isTrue() },
            { assertThat(result.detailImages).containsExactly("https://cdn.example.com/air-max.png") },
        )
    }

    @DisplayName("Like 포트 구현이 없으면 likedByMe 는 false 이다.")
    @Test
    fun listProductsUsesFalseLikedByMeWhenLikePortIsMissing() {
        val facade = ProductQueryFacade(
            catalogProductQueryPort = FakeCatalogProductQueryPort(),
            likeProductQueryPort = null,
            orderReservationQueryPort = null,
        )

        val result = facade.getProducts(sort = ProductSort.LATEST, page = 0, size = 20, userId = 7L)

        assertThat(result.map { it.likedByMe }).containsExactly(false, false)
    }

    private class FakeCatalogProductQueryPort : CatalogProductQueryPort {
        override fun findDisplayableProducts(sort: ProductSort, page: Int, size: Int): List<CatalogInfo.ProductDisplayRow> =
            listOf(
                CatalogInfo.ProductDisplayRow(1L, "Air Max", 1L, "Nike", 129000, 10, 3),
                CatalogInfo.ProductDisplayRow(2L, "Sold Out", 1L, "Nike", 99000, 2, 0),
            )

        override fun findDisplayableProductDetail(productId: Long): CatalogInfo.ProductDetailRow? =
            CatalogInfo.ProductDetailRow(
                product = CatalogInfo.ProductDisplayRow(productId, "Air Max", 1L, "Nike", 129000, 10, 5),
                detailImages = listOf("https://cdn.example.com/air-max.png"),
            )
    }

    private class FakeLikeProductQueryPort(private val likedProductIds: Set<Long>) : LikeProductQueryPort {
        override fun getLikedProductIds(userId: Long, productIds: Collection<Long>): Set<Long> =
            likedProductIds.intersect(productIds.toSet())

        override fun isLiked(userId: Long, productId: Long): Boolean = likedProductIds.contains(productId)
    }

    private class FakeOrderReservationQueryPort(private val activeReservedQuantity: Int) : OrderReservationQueryPort {
        override fun getActiveReservedQuantity(productId: Long): Int = activeReservedQuantity
    }
}
