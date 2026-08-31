package com.loopers.interfaces.api
import com.loopers.domain.order.Order
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.*
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) class OrderDiscountE2ETest @Autowired constructor(private val http:TestRestTemplate,private val jpa:OrderJpaRepository,private val cleanup:DatabaseCleanUp){
 @AfterEach fun clean(){cleanup.truncateAllTables()}
 @Test fun appliesAndRetriesThroughRealEntryPoint(){val o=jpa.save(Order(135135,10000));val h=HttpHeaders();h.set("X-USER-ID","135135");h.contentType=MediaType.APPLICATION_JSON;val req=HttpEntity(mapOf("couponId" to 10L),h);val url="/api/v1/orders/${o.id}/discount";val first=http.postForEntity(url,req,String::class.java);val retry=http.postForEntity(url,req,String::class.java);assertThat(first.statusCode).isEqualTo(HttpStatus.OK);assertThat(retry.body).isEqualTo(first.body);assertThat(first.body).contains("\"discountAmount\":1000","\"finalAmount\":9000")}
}
