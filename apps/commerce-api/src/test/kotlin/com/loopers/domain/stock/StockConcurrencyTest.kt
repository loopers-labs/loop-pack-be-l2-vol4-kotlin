package com.loopers.domain.stock
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
@SpringBootTest class StockConcurrencyTest @Autowired constructor(private val service:StockService,private val jpa:StockJpaRepository,private val cleanup:DatabaseCleanUp){
 @AfterEach fun clean(){cleanup.truncateAllTables()}
 @Test fun reconcilesEightRequests(){reconcile(8)}
 @Test fun reconcilesTwelveRequests(){reconcile(12)}
 private fun reconcile(requests:Int){val stock=jpa.save(StockModel(77,5));val ready=CountDownLatch(requests);val start=CountDownLatch(1);val done=CountDownLatch(requests);val success=AtomicInteger();val failure=AtomicInteger();Executors.newFixedThreadPool(requests).use{pool->repeat(requests){pool.submit{ready.countDown();try{start.await();service.decrease(stock.id,1);success.incrementAndGet()}catch(e:Exception){failure.incrementAndGet()}finally{done.countDown()}}};assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();assertThat(done.await(10,TimeUnit.SECONDS)).isTrue()};val remaining=jpa.findById(stock.id).orElseThrow().quantity;assertThat(success.get()+remaining).isEqualTo(5);assertThat(success.get()+failure.get()).isEqualTo(requests)}
}
