package com.loopers.domain.order.integration

import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.order.application.OrderFacade
import com.loopers.domain.order.infrastructure.persistence.OrderJpaRepository
import com.loopers.domain.order.support.OrderSteps.Companion.주문_생성_커맨드
import com.loopers.domain.order.support.OrderSteps.Companion.주문항목_생성_커맨드
import com.loopers.domain.product.application.ProductFacade
import com.loopers.domain.product.infrastructure.persistence.stock.ProductStockJpaRepository
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class OrderFacadeIntegrationTest
    @Autowired
    constructor(
        private val userService: UserService,
        private val brandService: BrandService,
        private val productFacade: ProductFacade,
        private val orderFacade: OrderFacade,
        private val orderJpaRepository: OrderJpaRepository,
        private val productStockJpaRepository: ProductStockJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `같은_멱등키의_주문은_한_번만_저장하고_재고도_한_번만_차감한다`() {
            val user = userService.signUp(사용자_회원가입())
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productFacade.registerProduct(
                상품_등록_커맨드(
                    brandId = brand.id,
                    initialStock = 10,
                ),
            )
            val command = 주문_생성_커맨드(
                userId = user.id,
                idempotencyKey = "order-key-1",
                items = listOf(주문항목_생성_커맨드(productId = product.id, quantity = 2)),
            )

            val first = orderFacade.placeOrder(command)
            val second = orderFacade.placeOrder(command)

            val savedStock = productStockJpaRepository.findById(product.id).orElseThrow()
            assertThat(second.id).isEqualTo(first.id)
            assertThat(orderJpaRepository.count()).isEqualTo(1)
            assertThat(savedStock.leftStock).isEqualTo(8)
        }
    }
