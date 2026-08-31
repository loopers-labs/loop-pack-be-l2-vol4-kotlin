package com.loopers.domain.order
import com.loopers.application.order.OrderDiscountFacade
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
@SpringBootTest class OrderDiscountPersistenceIntegrationTest @Autowired constructor(private val facade:OrderDiscountFacade,private val jpa:OrderJpaRepository,private val em:EntityManager,private val cleanup:DatabaseCleanUp){
 @AfterEach fun clean(){cleanup.truncateAllTables()}
 @Test fun preservesSnapshotAfterClearAndReload(){val o=jpa.save(Order(135135,10000));facade.apply(o.id,135135,10,Instant.parse("2026-08-31T23:59:59Z"));val confirmed=jpa.findById(o.id).orElseThrow();confirmed.confirm();jpa.saveAndFlush(confirmed);em.clear();val loaded=jpa.findById(o.id).orElseThrow();assertAll({assertThat(loaded.discountAmount).isEqualTo(1000)},{assertThat(loaded.finalAmount).isEqualTo(9000)},{assertThat(loaded.confirmed).isTrue()})}
 @Test fun rejectsOwnerAndExpiryBoundary(){val o=jpa.save(Order(135135,10000));assertThrows<RuntimeException>{facade.apply(o.id,1,10,Instant.parse("2026-08-31T23:59:59Z"))};assertThrows<RuntimeException>{facade.apply(o.id,135135,10,Instant.parse("2026-09-01T00:00:00Z"))}}
}
