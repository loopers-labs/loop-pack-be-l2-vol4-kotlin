package com.loopers.domain.like

import com.loopers.fixture.LikeModelFixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class LikeServiceTest {
    private val inMemoryLikeRepository = InMemoryLikeRepository()
    private val likeService = LikeService(
        inMemoryLikeRepository,
        InMemoryProductLikeCountRepository(),
    )

    @DisplayName("특정 상품의 좋아요 개수를 셀 때")
    @Nested
    internal inner class GetLikeCount {
        @DisplayName("좋아요가 없으면 0 을 반환한다.")
        @Test
        fun zeroWhenNoLike() {
            // when
            val count = likeService.getLikeCount(1L)

            // then
            assertThat(count).isEqualTo(0)
        }

        @DisplayName("해당 상품에 등록된 좋아요 개수만큼 반환한다.")
        @Test
        fun countByProductId() {
            // given
            inMemoryLikeRepository.saveForTest(LikeModelFixture.custom(userId = 1L, productId = 1L).toModel())
            inMemoryLikeRepository.saveForTest(LikeModelFixture.custom(userId = 2L, productId = 1L).toModel())
            inMemoryLikeRepository.saveForTest(LikeModelFixture.custom(userId = 3L, productId = 2L).toModel())

            // when
            val count = likeService.getLikeCount(1L)

            // then
            assertThat(count).isEqualTo(2)
        }

        @DisplayName("soft delete 된 좋아요는 카운트에서 제외된다.")
        @Test
        fun deletedLikeExcludedFromCount() {
            // given : available 1개 + soft delete 1개
            inMemoryLikeRepository.saveForTest(LikeModelFixture.custom(userId = 1L, productId = 1L).toModel())
            val deleted = LikeModelFixture.custom(userId = 2L, productId = 1L).toModel()
            deleted.delete()
            inMemoryLikeRepository.saveForTest(deleted)

            // when
            val count = likeService.getLikeCount(1L)

            // then : available() 필터로 삭제분은 제외되어 1
            assertThat(count).isEqualTo(1)
        }
    }

    @DisplayName("여러 상품의 좋아요 개수를 상품별로 묶어서 셀 때")
    @Nested
    internal inner class GetLikeCountGroupByProductId {
        @BeforeEach
        fun init() {
            // product 1 : available 2개
            inMemoryLikeRepository.saveForTest(LikeModelFixture.custom(userId = 1L, productId = 1L).toModel())
            inMemoryLikeRepository.saveForTest(LikeModelFixture.custom(userId = 2L, productId = 1L).toModel())
            // product 2 : available 1개 + soft delete 1개
            inMemoryLikeRepository.saveForTest(LikeModelFixture.custom(userId = 3L, productId = 2L).toModel())
            val deleted = LikeModelFixture.custom(userId = 4L, productId = 2L).toModel()
            deleted.delete()
            inMemoryLikeRepository.saveForTest(deleted)
        }

        @DisplayName("available() 한 좋아요만 상품별로 그룹핑하여 개수를 센다.")
        @Test
        fun groupingOnlyAvailable() {
            // when
            val result = likeService.getLikeCountGroupByProductId(listOf(1L, 2L))

            // then
            assertThat(result[ProductId(1L)]).isEqualTo(LikeCount(2))
            assertThat(result[ProductId(2L)]).isEqualTo(LikeCount(1))
        }

        @DisplayName("좋아요가 하나도 없는 상품은 결과 맵에 포함되지 않는다.")
        @Test
        fun absentWhenNoAvailableLike() {
            // when : product 3 은 좋아요 자체가 없음
            val result = likeService.getLikeCountGroupByProductId(listOf(1L, 2L, 3L))

            // then
            assertThat(result).doesNotContainKey(ProductId(3L))
            assertThat(result).containsOnlyKeys(ProductId(1L), ProductId(2L))
        }

        @DisplayName("available 한 좋아요가 없이 전부 삭제된 상품도 결과 맵에서 제외된다.")
        @Test
        fun absentWhenAllDeleted() {
            // given : product 9 의 좋아요는 모두 soft delete
            val deletedOnly = LikeModelFixture.custom(userId = 5L, productId = 9L).toModel()
            deletedOnly.delete()
            inMemoryLikeRepository.saveForTest(deletedOnly)

            // when
            val result = likeService.getLikeCountGroupByProductId(listOf(9L))

            // then
            assertThat(result).doesNotContainKey(ProductId(9L))
            assertThat(result).isEmpty()
        }
    }

    @DisplayName("좋아요를 등록할 때 (멱등 명세)")
    @Nested
    internal inner class AddLike {
        @DisplayName("처음 등록하면 LikeResult.Liked 를 반환하고 available 좋아요가 1개가 된다.")
        @Test
        fun newLike() {
            val created = likeService.addLike(1L, 1L)

            assertThat(created).isEqualTo(LikeResult.Liked)
            assertThat(likeService.getLikeCountGroupByProductId(listOf(1L))[ProductId(1L)]).isEqualTo(LikeCount(1))
        }

        @DisplayName("이미 좋아요한 상품에 다시 등록하면 LikeResult.AlreadyLiked 를 반환한다. (멱등 no-op)")
        @Test
        fun duplicateLike() {
            likeService.addLike(1L, 1L)

            assertThat(likeService.addLike(1L, 1L)).isEqualTo(LikeResult.AlreadyLiked)
        }

        @DisplayName("좋아요 취소 후 다시 등록하면 LikeResult.Liked 를 반환한다. (재등록 가능)")
        @Test
        fun reAddAfterRemove() {
            likeService.addLike(1L, 1L)
            likeService.remove(1L, 1L)

            assertThat(likeService.addLike(1L, 1L)).isEqualTo(LikeResult.Liked)
        }
    }

    @DisplayName("좋아요를 취소할 때 (멱등 명세)")
    @Nested
    internal inner class RemoveLike {
        @DisplayName("좋아요한 상품을 취소하면 available 좋아요에서 제외된다.")
        @Test
        fun removeExisting() {
            likeService.addLike(1L, 1L)
            likeService.remove(1L, 1L)

            assertThat(likeService.getLikeCountGroupByProductId(listOf(1L))).doesNotContainKey(ProductId(1L))
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도 예외가 발생하지 않는다. (멱등)")
        @Test
        fun removeNonExisting() {
            assertThatCode { likeService.remove(1L, 1L) }.doesNotThrowAnyException()
        }

        @DisplayName("이미 취소한 좋아요를 다시 취소해도 예외가 발생하지 않는다. (멱등)")
        @Test
        fun removeTwice() {
            likeService.addLike(1L, 1L)
            likeService.remove(1L, 1L)

            assertThatCode { likeService.remove(1L, 1L) }.doesNotThrowAnyException()
        }
    }
}
