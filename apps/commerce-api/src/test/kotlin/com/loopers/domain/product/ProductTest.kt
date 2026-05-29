package com.loopers.domain.product

import com.loopers.domain.shared.Money
import com.loopers.support.error.ConflictException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class ProductTest {
    private fun product() = Product(brandId = 1L, name = ProductName("에어맥스"), price = Money(100_000))

    @DisplayName("새로 생성한 상품의 좋아요 수는 0이고, 상태는 ACTIVE이다.")
    @Test
    fun likeCountIsZeroAndStatusActive_whenCreated() {
        assertAll(
            { assertThat(product().likeCount).isEqualTo(0) },
            { assertThat(product().status).isEqualTo(ProductStatus.ACTIVE) },
        )
    }

    @DisplayName("좋아요를 누르면, 좋아요 수가 1 증가한다.")
    @Test
    fun like_incrementsLikeCount() {
        val product = product()
        product.like()
        assertThat(product.likeCount).isEqualTo(1)
    }

    @DisplayName("좋아요를 취소하면, 좋아요 수가 1 감소한다.")
    @Test
    fun unlike_decrementsLikeCount() {
        val product = product()
        product.like()
        product.unlike()
        assertThat(product.likeCount).isEqualTo(0)
    }

    @DisplayName("좋아요 수가 0일 때 취소하면, 0을 유지한다(no-op).")
    @Test
    fun unlike_isNoOp_whenLikeCountIsZero() {
        val product = product()
        product.unlike()
        assertThat(product.likeCount).isEqualTo(0)
    }

    @DisplayName("상품을 수정하면, 이름과 가격은 변경되지만 브랜드 식별자는 불변이다.")
    @Test
    fun update_changesNameAndPrice_butNotBrandId() {
        val product = product()
        product.update(ProductName("줌플라이"), Money(150_000))
        assertAll(
            { assertThat(product.name).isEqualTo(ProductName("줌플라이")) },
            { assertThat(product.price).isEqualTo(Money(150_000)) },
            { assertThat(product.brandId).isEqualTo(1L) },
        )
    }

    @DisplayName("DELETED로 전이하면, status가 DELETED가 되고 deletedAt이 audit으로 기록된다.")
    @Test
    fun transitionToDeleted_setsStatusAndDeletedAt() {
        val product = product()
        product.transitionTo(ProductStatus.DELETED)
        assertAll(
            { assertThat(product.status).isEqualTo(ProductStatus.DELETED) },
            { assertThat(product.deletedAt).isNotNull() },
        )
    }

    @DisplayName("같은 상태로의 전이는 멱등하다(no-op).")
    @Test
    fun transitionTo_isIdempotent_forSameStatus() {
        val product = product()
        product.transitionTo(ProductStatus.DELETED)
        val firstDeletedAt = product.deletedAt
        product.transitionTo(ProductStatus.DELETED)
        assertAll(
            { assertThat(product.status).isEqualTo(ProductStatus.DELETED) },
            { assertThat(product.deletedAt).isEqualTo(firstDeletedAt) },
        )
    }

    @DisplayName("허용되지 않은 상태 전이(DELETED→ACTIVE)는 CONFLICT 예외가 발생한다.")
    @Test
    fun transitionTo_throwsConflict_forDisallowedTransition() {
        val product = product()
        product.transitionTo(ProductStatus.DELETED)
        val result = assertThrows<ConflictException> {
            product.transitionTo(ProductStatus.ACTIVE)
        }
        assertThat(result.errorCode).isEqualTo(ProductErrorCode.INVALID_PRODUCT_STATUS_TRANSITION)
    }
}
