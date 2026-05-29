package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorCode
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.shared.IdCursor
import com.loopers.domain.shared.Money
import com.loopers.support.error.BadRequestException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
class ProductRepositoryIntegrationTest @Autowired constructor(
    private val productJpaRepository: ProductJpaRepository,
) {
    private val productRepository = ProductRepositoryImpl(productJpaRepository)

    private fun save(name: String, price: Long, brandId: Long = 1L, likes: Int = 0): Product {
        val product = Product(brandId = brandId, name = ProductName(name), price = Money(price))
        repeat(likes) { product.like() }
        return productJpaRepository.save(product)
    }

    @DisplayName("상품을 저장하면, 활성 단건 조회로 찾을 수 있다.")
    @Test
    fun findsActiveProduct_whenSaved() {
        val saved = save("에어맥스", 100_000)

        val found = productRepository.findActiveById(saved.id)

        assertThat(found?.name).isEqualTo(ProductName("에어맥스"))
    }

    @DisplayName("DELETED 상태의 상품은, 활성 단건 조회로 찾을 수 없다.")
    @Test
    fun doesNotFindDeletedProduct() {
        val product = Product(brandId = 1L, name = ProductName("에어맥스"), price = Money(100_000))
            .also { it.transitionTo(ProductStatus.DELETED) }
        val saved = productJpaRepository.save(product)

        assertThat(productRepository.findActiveById(saved.id)).isNull()
    }

    @DisplayName("브랜드의 활성 상품만 조회한다(cascade 대상).")
    @Test
    fun findsActiveProductsByBrandId() {
        val first = save("나이키1", 100, brandId = 1L)
        val second = save("나이키2", 200, brandId = 1L)
        save("아디다스", 100, brandId = 2L)
        productJpaRepository.save(
            Product(brandId = 1L, name = ProductName("삭제됨"), price = Money(100))
                .also { it.transitionTo(ProductStatus.DELETED) },
        )

        val found = productRepository.findActiveByBrandId(1L)

        assertThat(found.map { it.id }).containsExactlyInAnyOrder(first.id, second.id)
    }

    @DisplayName("latest 정렬은 id DESC로 반환한다.")
    @Test
    fun listLatest_ordersByIdDesc() {
        val first = save("상품1", 100)
        val second = save("상품2", 200)
        val third = save("상품3", 300)

        val page = productRepository.findAll(ProductSort.LATEST, brandId = null, cursor = null, size = 10)

        assertThat(page.content.map { it.id }).containsExactly(third.id, second.id, first.id)
    }

    @DisplayName("price_asc 정렬은 가격 오름차순이며, 동일 가격은 id DESC로 타이브레이크한다.")
    @Test
    fun listPriceAsc_ordersByPriceAscThenIdDesc() {
        val cheapLow = save("저가A", 100)
        val cheapHigh = save("저가B", 100)
        val expensive = save("고가", 200)

        val page = productRepository.findAll(ProductSort.PRICE_ASC, brandId = null, cursor = null, size = 10)

        assertThat(page.content.map { it.id })
            .containsExactly(cheapHigh.id, cheapLow.id, expensive.id)
    }

    @DisplayName("likes_desc 정렬은 좋아요 수 내림차순이며, 동일 좋아요 수는 id DESC로 타이브레이크한다.")
    @Test
    fun listLikesDesc_ordersByLikeCountDescThenIdDesc() {
        val twoLow = save("좋아요2A", 100, likes = 2)
        val twoHigh = save("좋아요2B", 100, likes = 2)
        val five = save("좋아요5", 100, likes = 5)

        val page = productRepository.findAll(ProductSort.LIKES_DESC, brandId = null, cursor = null, size = 10)

        assertThat(page.content.map { it.id })
            .containsExactly(five.id, twoHigh.id, twoLow.id)
    }

    @DisplayName("brandId 필터는 해당 브랜드의 상품만 반환한다.")
    @Test
    fun listFiltersByBrandId() {
        val nike = save("나이키상품", 100, brandId = 1L)
        save("아디다스상품", 100, brandId = 2L)

        val page = productRepository.findAll(ProductSort.LATEST, brandId = 1L, cursor = null, size = 10)

        assertThat(page.content.map { it.id }).containsExactly(nike.id)
    }

    @DisplayName("페이지 결과에서 DELETED 상태의 상품은 제외된다.")
    @Test
    fun excludesDeletedProduct_fromPage() {
        val active = save("활성", 100)
        productJpaRepository.save(
            Product(brandId = 1L, name = ProductName("삭제"), price = Money(100))
                .also { it.transitionTo(ProductStatus.DELETED) },
        )

        val page = productRepository.findAll(ProductSort.LATEST, brandId = null, cursor = null, size = 10)

        assertThat(page.content.map { it.id }).containsExactly(active.id)
    }

    @DisplayName("latest nextCursor로 다음 페이지를 조회하면, 이전 페이지를 제외하고 이어진다.")
    @Test
    fun latestCursor_continuesWithoutOverlap() {
        val first = save("상품1", 100)
        val second = save("상품2", 200)
        val third = save("상품3", 300)

        val firstPage = productRepository.findAll(ProductSort.LATEST, brandId = null, cursor = null, size = 2)
        val secondPage = productRepository.findAll(ProductSort.LATEST, brandId = null, cursor = firstPage.nextCursor, size = 2)

        assertAll(
            { assertThat(firstPage.content.map { it.id }).containsExactly(third.id, second.id) },
            { assertThat(firstPage.hasNext).isTrue() },
            { assertThat(firstPage.nextCursor).isNotNull() },
            { assertThat(secondPage.content.map { it.id }).containsExactly(first.id) },
            { assertThat(secondPage.hasNext).isFalse() },
            { assertThat(secondPage.nextCursor).isNull() },
        )
    }

    @DisplayName("정렬과 맞지 않는 커서 타입을 주면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenCursorTypeMismatchesSort() {
        val result = assertThrows<BadRequestException> {
            productRepository.findAll(ProductSort.PRICE_ASC, brandId = null, cursor = IdCursor(1L), size = 10)
        }
        assertThat(result.errorCode).isEqualTo(ProductErrorCode.INVALID_PRODUCT_CURSOR)
    }

    @DisplayName("price_asc nextCursor(복합 키셋)로 다음 페이지를 조회하면, 가격·id 키셋으로 이어진다.")
    @Test
    fun priceAscCompositeCursor_continuesWithoutOverlap() {
        val p100Low = save("A", 100)
        val p100High = save("B", 100)
        val p200 = save("C", 200)
        val p300 = save("D", 300)

        val firstPage = productRepository.findAll(ProductSort.PRICE_ASC, brandId = null, cursor = null, size = 2)
        val secondPage = productRepository.findAll(ProductSort.PRICE_ASC, brandId = null, cursor = firstPage.nextCursor, size = 2)

        assertAll(
            { assertThat(firstPage.content.map { it.id }).containsExactly(p100High.id, p100Low.id) },
            { assertThat(firstPage.hasNext).isTrue() },
            { assertThat(secondPage.content.map { it.id }).containsExactly(p200.id, p300.id) },
            { assertThat(secondPage.hasNext).isFalse() },
        )
    }
}
