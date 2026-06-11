package com.loopers.application.order

import com.loopers.domain.catalog.ProductStock
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.StockReservationStatus
import com.loopers.infrastructure.catalog.ProductStockJpaRepository
import com.loopers.infrastructure.order.StockReservationJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class StockApplicationServiceTest @Autowired constructor(
    private val stockApplicationService: StockApplicationService,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val stockReservationJpaRepository: StockReservationJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun reserveAllCreatesInProgressReservationsAndIncreasesReservedQuantity() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        productStockJpaRepository.save(ProductStock(productId = 20L, stockQuantity = 3))

        stockApplicationService.reserveAll(
            orderId = 1L,
            items = listOf(
                OrderCommand.CheckoutItem(20L, "상품B", "브랜드B", 2000L, 2),
                OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 1),
            ),
        )

        val reservations = stockReservationJpaRepository.findAllByOrderId(1L)
        val firstStock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        val secondStock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(20L)!!
        assertAll(
            { assertThat(reservations).hasSize(2) },
            { assertThat(reservations).allMatch { it.status == StockReservationStatus.IN_PROGRESS } },
            { assertThat(firstStock.stockQuantity).isEqualTo(5) },
            { assertThat(firstStock.reservedQuantity).isEqualTo(1) },
            { assertThat(secondStock.stockQuantity).isEqualTo(3) },
            { assertThat(secondStock.reservedQuantity).isEqualTo(2) },
        )
    }

    @Test
    fun reserveAllThrowsConflictWhenActiveReservationsConsumeAvailableStock() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 2))
        stockApplicationService.reserveAll(
            orderId = 1L,
            items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 2)),
        )

        val ex = assertThrows<CoreException> {
            stockApplicationService.reserveAll(
                orderId = 2L,
                items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 1)),
            )
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    @Test
    fun confirmAndDeductChangesReservationToCompletedAndDecreasesActualAndReservedStock() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        stockApplicationService.reserveAll(
            orderId = 1L,
            items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 2)),
        )

        stockApplicationService.confirmAndDeduct(orderId = 1L)

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(1L).single()
        assertAll(
            { assertThat(stock.stockQuantity).isEqualTo(3) },
            { assertThat(stock.reservedQuantity).isZero() },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.COMPLETED) },
        )
    }

    @Test
    fun cancelInProgressReleasesReservedQuantityAndCancelsReservation() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        stockApplicationService.reserveAll(
            orderId = 1L,
            items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 2)),
        )

        stockApplicationService.cancelInProgress(orderId = 1L)

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(1L).single()
        assertAll(
            { assertThat(stock.stockQuantity).isEqualTo(5) },
            { assertThat(stock.reservedQuantity).isZero() },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED) },
        )
    }

    @Test
    fun expireInProgressReleasesReservedQuantityAndExpiresReservation() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        stockApplicationService.reserveAll(
            orderId = 1L,
            items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 2)),
        )

        stockApplicationService.expireInProgress(orderId = 1L)

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(1L).single()
        assertAll(
            { assertThat(stock.stockQuantity).isEqualTo(5) },
            { assertThat(stock.reservedQuantity).isZero() },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.EXPIRED) },
        )
    }

    @Test
    fun cancelCompletedRestoresActualStockAndCancelsCompletedReservation() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        stockApplicationService.reserveAll(
            orderId = 1L,
            items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 2)),
        )
        stockApplicationService.confirmAndDeduct(orderId = 1L)

        stockApplicationService.cancelCompletedAndRestore(orderId = 1L)

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(1L).single()
        assertAll(
            { assertThat(stock.stockQuantity).isEqualTo(5) },
            { assertThat(stock.reservedQuantity).isZero() },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED) },
        )
    }
}
