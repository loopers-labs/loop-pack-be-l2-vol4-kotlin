package com.loopers.application.like

import com.loopers.domain.like.LikeAction
import com.loopers.domain.like.ProductLikeCursor
import com.loopers.domain.like.ProductLikeCursorRepository
import com.loopers.domain.like.ProductLikeHistory
import com.loopers.domain.like.ProductLikeHistoryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.util.concurrent.atomic.AtomicLong

class ProductLikeApplicationServiceTest {
    private val historyRepository = FakeProductLikeHistoryRepository()
    private val cursorRepository = FakeProductLikeCursorRepository()
    private val service = ProductLikeApplicationService(historyRepository, cursorRepository)

    @DisplayName("register 를 호출할 때,")
    @Nested
    inner class Register {
        @DisplayName("좋아요하지 않은 상품이면 REGISTER 이력을 추가하고 changed=true 를 반환한다.")
        @Test
        fun createsRegisterHistory_whenNotLiked() {
            val result = service.register(userId = 1L, productId = 100L)

            assertAll(
                { assertThat(result).isEqualTo(ProductLikeResult(productId = 100L, liked = true, changed = true)) },
                { assertThat(historyRepository.historiesFor(1L, 100L).map { it.action }).containsExactly(LikeAction.REGISTER) },
                { assertThat(cursorRepository.cursor(1L, 100L)?.lastHistoryId).isEqualTo(historyRepository.historiesFor(1L, 100L).single().id) },
            )
        }

        @DisplayName("이미 좋아요한 상품이면 이력을 추가하지 않고 changed=false 를 반환한다.")
        @Test
        fun doesNotCreateHistory_whenAlreadyLiked() {
            service.register(userId = 1L, productId = 100L)

            val result = service.register(userId = 1L, productId = 100L)

            assertAll(
                { assertThat(result).isEqualTo(ProductLikeResult(productId = 100L, liked = true, changed = false)) },
                { assertThat(historyRepository.historiesFor(1L, 100L)).hasSize(1) },
            )
        }

        @DisplayName("취소 상태 상품이면 REGISTER 이력을 추가한다.")
        @Test
        fun createsRegisterHistory_afterCancel() {
            service.register(userId = 1L, productId = 100L)
            service.cancel(userId = 1L, productId = 100L)

            val result = service.register(userId = 1L, productId = 100L)

            assertAll(
                { assertThat(result.changed).isTrue() },
                {
                    assertThat(historyRepository.historiesFor(1L, 100L).map { it.action })
                        .containsExactly(LikeAction.REGISTER, LikeAction.CANCEL, LikeAction.REGISTER)
                },
            )
        }
    }

    @DisplayName("cancel 을 호출할 때,")
    @Nested
    inner class Cancel {
        @DisplayName("좋아요한 상품이면 CANCEL 이력을 추가하고 changed=true 를 반환한다.")
        @Test
        fun createsCancelHistory_whenLiked() {
            service.register(userId = 1L, productId = 100L)

            val result = service.cancel(userId = 1L, productId = 100L)

            assertAll(
                { assertThat(result).isEqualTo(ProductLikeResult(productId = 100L, liked = false, changed = true)) },
                {
                    assertThat(historyRepository.historiesFor(1L, 100L).map { it.action })
                        .containsExactly(LikeAction.REGISTER, LikeAction.CANCEL)
                },
            )
        }

        @DisplayName("이력이 없으면 이력을 추가하지 않고 changed=false 를 반환한다.")
        @Test
        fun doesNotCreateHistory_whenNeverLiked() {
            val result = service.cancel(userId = 1L, productId = 100L)

            assertAll(
                { assertThat(result).isEqualTo(ProductLikeResult(productId = 100L, liked = false, changed = false)) },
                { assertThat(historyRepository.historiesFor(1L, 100L)).isEmpty() },
            )
        }

        @DisplayName("이미 취소 상태이면 이력을 추가하지 않고 changed=false 를 반환한다.")
        @Test
        fun doesNotCreateHistory_whenAlreadyCanceled() {
            service.register(userId = 1L, productId = 100L)
            service.cancel(userId = 1L, productId = 100L)

            val result = service.cancel(userId = 1L, productId = 100L)

            assertAll(
                { assertThat(result.changed).isFalse() },
                { assertThat(historyRepository.historiesFor(1L, 100L)).hasSize(2) },
            )
        }
    }

    @DisplayName("현재 상태를 조회할 때,")
    @Nested
    inner class Query {
        @DisplayName("REGISTER 가 최신 이력인 상품만 liked 로 반환한다.")
        @Test
        fun returnsOnlyCurrentlyLikedProductIds() {
            service.register(userId = 1L, productId = 100L)
            service.register(userId = 1L, productId = 200L)
            service.cancel(userId = 1L, productId = 200L)

            val result = service.getLikedProductIds(userId = 1L, productIds = listOf(100L, 200L, 300L))

            assertThat(result).containsExactly(100L)
        }

        @DisplayName("isLiked 는 최신 이력이 REGISTER 인 경우에만 true 를 반환한다.")
        @Test
        fun returnsCurrentLikedState() {
            service.register(userId = 1L, productId = 100L)
            service.register(userId = 1L, productId = 200L)
            service.cancel(userId = 1L, productId = 200L)

            assertAll(
                { assertThat(service.isLiked(userId = 1L, productId = 100L)).isTrue() },
                { assertThat(service.isLiked(userId = 1L, productId = 200L)).isFalse() },
                { assertThat(service.isLiked(userId = 1L, productId = 300L)).isFalse() },
            )
        }
    }

    private class FakeProductLikeHistoryRepository : ProductLikeHistoryRepository {
        private val sequence = AtomicLong(1)
        private val store = mutableMapOf<Long, ProductLikeHistory>()

        override fun findById(id: Long): ProductLikeHistory? = store[id]

        override fun findLatest(userId: Long, productId: Long): ProductLikeHistory? =
            store.values
                .filter { it.userId == userId && it.productId == productId }
                .maxByOrNull { it.id }

        override fun save(history: ProductLikeHistory): ProductLikeHistory {
            val existingId = idField.getLong(history)
            if (existingId == 0L) {
                idField.setLong(history, sequence.getAndIncrement())
            }
            store[history.id] = history
            return history
        }

        override fun findLikedProductIds(userId: Long, productIds: Collection<Long>): Set<Long> =
            productIds.filter { productId -> findLatest(userId, productId)?.action == LikeAction.REGISTER }.toSet()

        fun historiesFor(userId: Long, productId: Long): List<ProductLikeHistory> =
            store.values.filter { it.userId == userId && it.productId == productId }.sortedBy { it.id }

        companion object {
            private val idField = com.loopers.domain.BaseEntity::class.java.getDeclaredField("id").apply { isAccessible = true }
        }
    }

    private class FakeProductLikeCursorRepository : ProductLikeCursorRepository {
        private val store = mutableMapOf<Pair<Long, Long>, ProductLikeCursor>()

        override fun findOrCreateForUpdate(userId: Long, productId: Long): ProductLikeCursor =
            store.getOrPut(userId to productId) { ProductLikeCursor(userId = userId, productId = productId) }

        override fun save(cursor: ProductLikeCursor): ProductLikeCursor {
            store[cursor.userId to cursor.productId] = cursor
            return cursor
        }

        fun cursor(userId: Long, productId: Long): ProductLikeCursor? = store[userId to productId]
    }
}
