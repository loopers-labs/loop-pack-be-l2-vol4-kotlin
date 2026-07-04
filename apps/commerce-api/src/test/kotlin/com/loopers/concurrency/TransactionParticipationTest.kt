package com.loopers.concurrency

import com.loopers.application.order.OrderFacade
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponService
import com.loopers.domain.order.OrderService
import com.loopers.domain.product.Level
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.TechCategory
import com.loopers.domain.stock.StockModel
import com.loopers.domain.user.UserService
import com.loopers.fixture.UserModelFixture
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.SpykBean
import com.zaxxer.hikari.HikariDataSource
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.ZonedDateTime
import javax.sql.DataSource

@SpringBootTest
class TransactionParticipationTest {
    data class Snap(val tx: String?, val readOnly: Boolean, val activeConn: Int, val connId: Int, val thread: String)

    @SpykBean private lateinit var userService: UserService
    @SpykBean private lateinit var userCouponService: UserCouponService
    @SpykBean private lateinit var orderService: OrderService

    @Autowired private lateinit var orderFacade: OrderFacade
    @Autowired private lateinit var userJpaRepository: UserJpaRepository
    @Autowired private lateinit var productJpaRepository: ProductJpaRepository
    @Autowired private lateinit var stockJpaRepository: StockJpaRepository
    @Autowired private lateinit var couponJpaRepository: CouponJpaRepository
    @Autowired private lateinit var userCouponJpaRepository: UserCouponJpaRepository
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var databaseCleanUp: DatabaseCleanUp

    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun saveUser() = userJpaRepository.save(UserModelFixture.defaults().toModel())

    private var productSequence = 0

    private fun saveProductWithStock(price: Double, quantity: Int): ProductModel {
        val product = productJpaRepository.save(
            ProductModel.of(
                brandId = 1L,
                isbn = "isbn-${++productSequence}",
                name = "테스트 상품 $productSequence",
                authName = "저자",
                techCategory = TechCategory.BACKEND,
                level = Level.BEGINNER,
                price = price,
                description = "설명",
            ),
        )
        stockJpaRepository.save(StockModel.of(product.id, quantity))
        return product
    }

    private fun saveCoupon(minOrderAmount: Double = 10000.0) =
        couponJpaRepository.save(CouponModel.of("쿠폰", CouponType.FIXED, 3000.0, minOrderAmount, ZonedDateTime.now().plusDays(1), 100))

    private fun saveUserCoupon(coupon: CouponModel, userId: Long, used: Boolean = false) =
        userCouponJpaRepository.save(UserCouponModel.of(coupon, userId).apply { if (used) use() })

    @Test
    fun trxTest() {
        // 호출 순간의 트랜잭션·커넥션 상태 캡처
        val captured = linkedMapOf<String, Snap>()
        fun snapshot() = Snap(
            tx = TransactionSynchronizationManager.getCurrentTransactionName(),
            readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
            activeConn = dataSource.unwrap(HikariDataSource::class.java).hikariPoolMXBean.activeConnections,
            connId = System.identityHashCode(DataSourceUtils.getConnection(dataSource)),
            thread = Thread.currentThread().name,
        )

        // callOriginal() 이후에 캡처 — Hibernate 지연 획득이라 쿼리 실행 후라야 커넥션이 잡혀 있음
        every { userService.getByLoginId(any()) } answers { callOriginal().also { captured["user(readOnly)"] = snapshot() } }
        every { userCouponService.getWithLockById(any()) } answers { callOriginal().also { captured["coupon(무선언)"] = snapshot() } }
        every { orderService.createOrder(any(), any(), any(), any()) } answers { callOriginal().also { captured["order(쓰기)"] = snapshot() } }

        // given
        val user = saveUser()
        val product = saveProductWithStock(price = 10000.0, quantity = 10)
        val userCoupon = saveUserCoupon(saveCoupon(), user.id)

        // when
        orderFacade.placeOrder(user.loginId, listOf(product.id to 1), userCoupon.id)

        // then
        println("=== 트랜잭션 참여 + 커넥션 캡처 ===")
        captured.forEach { (svc, s) ->
            println("$svc -> tx=${s.tx}, readOnly=${s.readOnly}, activeConn=${s.activeConn}, connId=${s.connId}, thread=${s.thread}")
        }

        assertAll(
            // ① readOnly 서비스인데 쓰기 트랜잭션에 참여 → readOnly 무시
            { assertThat(captured["user(readOnly)"]!!.readOnly).isFalse() },
            // ② 모든 서비스가 같은 트랜잭션에 속함 → 참여 (새 트랜잭션 안 열림)
            { assertThat(captured.values.map { it.tx }.distinct()).hasSize(1) },
            // ③ 모든 시점에서 활성 커넥션은 1개 → 물리 커넥션도 공유
            { assertThat(captured.values.map { it.activeConn }).containsOnly(1) },
            // ④ 모든 시점에서 같은 커넥션 객체 + 같은 스레드 → 동일 커넥션 재사용
            { assertThat(captured.values.map { it.connId }.distinct()).hasSize(1) },
            { assertThat(captured.values.map { it.thread }.distinct()).hasSize(1) },
        )
    }
}
