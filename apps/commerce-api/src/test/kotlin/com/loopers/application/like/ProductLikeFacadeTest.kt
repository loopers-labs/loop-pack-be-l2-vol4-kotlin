package com.loopers.application.like

import com.loopers.application.catalog.port.CatalogProductStatsCommandPort
import com.loopers.domain.like.LikeAction
import com.loopers.domain.like.ProductLikeCursor
import com.loopers.domain.like.ProductLikeCursorRepository
import com.loopers.domain.like.ProductLikeHistory
import com.loopers.domain.like.ProductLikeHistoryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.atomic.AtomicLong

class ProductLikeFacadeTest {
    private val historyRepository = FakeProductLikeHistoryRepository()
    private val cursorRepository = FakeProductLikeCursorRepository()
    private val likeService = ProductLikeApplicationService(historyRepository, cursorRepository)
    private val statsCommandPort = RecordingCatalogProductStatsCommandPort()
    private val facade = ProductLikeFacade(likeService, statsCommandPort, TransactionTemplate(NoOpTransactionManager()))

    @DisplayName("register 는 실제 REGISTER 전이가 발생한 경우에만 Catalog likeCount 증가를 요청한다.")
    @Test
    fun increasesStatsOnlyOnRealRegisterTransition() {
        facade.register(userId = 1L, productId = 100L)
        facade.register(userId = 1L, productId = 100L)

        assertAll(
            { assertThat(statsCommandPort.increasedProductIds).containsExactly(100L) },
            { assertThat(statsCommandPort.decreasedProductIds).isEmpty() },
        )
    }

    @DisplayName("cancel 은 실제 CANCEL 전이가 발생한 경우에만 Catalog likeCount 감소를 요청한다.")
    @Test
    fun decreasesStatsOnlyOnRealCancelTransition() {
        facade.cancel(userId = 1L, productId = 100L)
        facade.register(userId = 1L, productId = 100L)
        facade.cancel(userId = 1L, productId = 100L)
        facade.cancel(userId = 1L, productId = 100L)

        assertAll(
            { assertThat(statsCommandPort.increasedProductIds).containsExactly(100L) },
            { assertThat(statsCommandPort.decreasedProductIds).containsExactly(100L) },
        )
    }

    private class RecordingCatalogProductStatsCommandPort : CatalogProductStatsCommandPort {
        val increasedProductIds = mutableListOf<Long>()
        val decreasedProductIds = mutableListOf<Long>()

        override fun increaseLikeCount(productId: Long) {
            increasedProductIds += productId
        }

        override fun decreaseLikeCount(productId: Long) {
            decreasedProductIds += productId
        }
    }

    private class NoOpTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()

        override fun commit(status: TransactionStatus) = Unit

        override fun rollback(status: TransactionStatus) = Unit
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
    }
}
