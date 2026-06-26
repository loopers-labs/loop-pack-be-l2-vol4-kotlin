package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.ProductStock
import com.loopers.domain.catalog.ProductStockRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductStockRepositoryImplIntegrationTest @Autowired constructor(
    private val productStockRepository: ProductStockRepository,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun reserveIfAvailableIncreasesReservedQuantityOnlyWhenAvailableStockIsEnough() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))

        val first = productStockRepository.reserveIfAvailable(productId = 10L, quantity = 3)
        val second = productStockRepository.reserveIfAvailable(productId = 10L, quantity = 3)

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(first).isTrue() },
            { assertThat(second).isFalse() },
            { assertThat(stock.stockQuantity).isEqualTo(5) },
            { assertThat(stock.reservedQuantity).isEqualTo(3) },
        )
    }

    @Test
    fun confirmReservedDecreasesActualAndReservedTogether() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5, reservedQuantity = 3))

        val confirmed = productStockRepository.confirmReserved(productId = 10L, quantity = 2)

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(confirmed).isTrue() },
            { assertThat(stock.stockQuantity).isEqualTo(3) },
            { assertThat(stock.reservedQuantity).isEqualTo(1) },
        )
    }

    @Test
    fun releaseReservedDecreasesOnlyReservedQuantity() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5, reservedQuantity = 3))

        val released = productStockRepository.releaseReserved(productId = 10L, quantity = 2)

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(released).isTrue() },
            { assertThat(stock.stockQuantity).isEqualTo(5) },
            { assertThat(stock.reservedQuantity).isEqualTo(1) },
        )
    }

    @Test
    fun restoreActualStockIncreasesOnlyActualQuantity() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 3, reservedQuantity = 0))

        val restored = productStockRepository.restoreActualStock(productId = 10L, quantity = 2)

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(restored).isTrue() },
            { assertThat(stock.stockQuantity).isEqualTo(5) },
            { assertThat(stock.reservedQuantity).isZero() },
        )
    }
}
