package com.loopers.infrastructure.like

import com.loopers.domain.like.ProductLike
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException

@DataJpaTest
class ProductLikeRepositoryIntegrationTest @Autowired constructor(
    private val productLikeJpaRepository: ProductLikeJpaRepository,
) {
    private val repository = ProductLikeRepositoryImpl(productLikeJpaRepository)

    @DisplayName("저장한 좋아요는 (userId, productId)로 조회되고, 존재 여부도 참이다.")
    @Test
    fun savesAndFindsByUserIdAndProductId() {
        repository.save(ProductLike(10L, 100L))

        assertAll(
            { assertThat(repository.findByUserIdAndProductId(10L, 100L)).isNotNull() },
            { assertThat(repository.existsByUserIdAndProductId(10L, 100L)).isTrue() },
            { assertThat(repository.existsByUserIdAndProductId(10L, 999L)).isFalse() },
        )
    }

    @DisplayName("같은 (userId, productId)를 중복 저장하면, DB unique 제약으로 실패한다.")
    @Test
    fun throwsDataIntegrityViolation_whenUserProductIsDuplicated() {
        productLikeJpaRepository.saveAndFlush(ProductLike(10L, 100L))

        assertThrows<DataIntegrityViolationException> {
            productLikeJpaRepository.saveAndFlush(ProductLike(10L, 100L))
        }
    }

    @DisplayName("좋아요를 삭제하면, 더 이상 조회되지 않는다.")
    @Test
    fun removesRow_whenDeleted() {
        val saved = repository.save(ProductLike(10L, 100L))

        repository.delete(saved)

        assertThat(repository.findByUserIdAndProductId(10L, 100L)).isNull()
    }

    @DisplayName("본인 좋아요 목록은 최근 등록 순(id DESC)으로 반환한다.")
    @Test
    fun returnsOwnLikes_orderedByIdDesc() {
        val saved = (1..3).map { repository.save(ProductLike(10L, it.toLong())) }

        val likes = repository.findAllByUserId(10L)

        assertThat(likes.map { it.id }).containsExactly(saved[2].id, saved[1].id, saved[0].id)
    }

    @DisplayName("목록은 본인(userId)의 좋아요만 포함한다.")
    @Test
    fun includesOnlyOwnLikes() {
        repository.save(ProductLike(10L, 100L))
        repository.save(ProductLike(99L, 200L))

        val likes = repository.findAllByUserId(10L)

        assertThat(likes.map { it.productId }).containsExactly(100L)
    }
}
