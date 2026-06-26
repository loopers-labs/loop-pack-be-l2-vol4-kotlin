package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.order.application.OrderFacade
import com.loopers.domain.order.support.OrderSteps.Companion.주문_생성_커맨드
import com.loopers.domain.order.support.OrderSteps.Companion.주문항목_생성_커맨드
import com.loopers.domain.payment.port.PaymentGatewayPort
import com.loopers.domain.product.application.ProductFacade
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.기본_로그인_ID
import com.loopers.domain.user.support.UserSteps.Companion.기본_비밀번호
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.io.File
import java.nio.file.Path
import java.time.Duration

/**
 * 실제 pg-simulator 모듈을 컨테이너로 구동하여 외부 연동 전체 경로(요청 + 상태확인 API)를 검증하는 Live 통합 테스트.
 *
 * 설계 의도:
 * - commerce-api 와 pg-simulator 는 패키지 루트(`com.loopers`)와 동일 FQN 클래스(ApiResponse, ErrorType 등)를
 *   공유하므로, 한 JVM/클래스패스에 함께 올릴 수 없다. 따라서 pg-simulator 의 bootJar 를 별도 컨테이너로 띄워
 *   클래스패스를 분리한다. (pg-simulator 모듈 코드는 변경하지 않는다.)
 * - bootJar 경로는 build.gradle.kts 의 `tasks.test` 에서 `pg.simulator.libs` 시스템 프로퍼티로 주입하며,
 *   `:apps:pg-simulator:bootJar` 산출물을 `dependsOn` 으로 선행 빌드한다.
 *
 * 실행 조건:
 * - Docker 가 없는 환경에서는 `@Testcontainers(disabledWithoutDocker = true)` 로 클래스 전체가 자동 스킵된다.
 *   (CI / 로컬 Docker 환경에서만 실제 구동·검증된다.)
 *
 * 검증 범위(요청 + 상태확인 레그):
 * - 실제 시뮬레이터로 결제 요청이 도달하여 거래키가 발급되는지
 * - 발급된 거래키를 상태확인 API(`getTransaction`, `findByOrderId`)로 실제 컨테이너에서 재조회할 수 있는지
 * - pg-simulator 는 의도적으로 40% 요청 실패 + 100~500ms 지연을 주입하므로, 성공 응답을 얻을 때까지 제한적으로
 *   재시도하고, 끝내 성공하지 못하면(확률적 인프라 장애) 단정 대신 가정 실패로 처리해 플래키를 방지한다.
 */
@Testcontainers(disabledWithoutDocker = true)
class PaymentPgLiveIntegrationTest
    @Autowired
    constructor(
        private val userService: UserService,
        private val brandService: BrandService,
        private val productFacade: ProductFacade,
        private val orderFacade: OrderFacade,
        private val paymentGatewayPort: PaymentGatewayPort,
    ) : ApiTest() {
        private val mapResponseType =
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}

        @Test
        fun `실제_pg-simulator_컨테이너에_결제를_요청하고_상태확인_API로_거래를_조회한다`() {
            val userId = userService.signUp(사용자_회원가입()).id
            val orderId = placePendingOrder(userId)

            // 40% 요청 실패 + 지연이 주입된 실제 시뮬레이터이므로 성공할 때까지 제한적으로 재시도한다.
            // 거래키가 발급되면 즉시 중단한다(같은 주문에 다른 거래키를 재할당하면 도메인 예외가 발생하므로).
            var transactionKey: String? = null
            repeat(MAX_REQUEST_ATTEMPTS) {
                if (transactionKey != null) return@repeat
                val response = requestPayment(orderId = orderId, userId = userId)
                if (response.statusCode == HttpStatus.OK) {
                    transactionKey = response.body?.data?.get("transactionKey") as String?
                }
            }

            assumeTrue(transactionKey != null, "pg-simulator 가 반복 실패하여 성공 응답을 얻지 못했습니다.")
            val confirmedKey = transactionKey!!

            // 상태확인 API 레그: 실제 컨테이너에 대해 어댑터의 조회 메서드(getTransaction / findByOrderId)가 동작하는지 검증
            val byOrder = paymentGatewayPort.findByOrderId(userId, orderId)
            assertThat(byOrder).anyMatch { it.transactionKey == confirmedKey }

            val detail = paymentGatewayPort.getTransaction(userId, confirmedKey)
            assertThat(detail.transactionKey).isEqualTo(confirmedKey)
        }

        private fun placePendingOrder(userId: Long): Long {
            val brand = brandService.register(브랜드_등록_커맨드())
            val productId = productFacade.registerProduct(
                상품_등록_커맨드(
                    brandId = brand.id,
                    price = 10_000,
                    initialStock = 5,
                ),
            ).id
            return orderFacade.placeOrder(
                주문_생성_커맨드(
                    userId = userId,
                    items = listOf(주문항목_생성_커맨드(productId = productId, quantity = 1)),
                ),
            ).id
        }

        private fun requestPayment(orderId: Long, userId: Long) =
            testRestTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                HttpEntity(
                    mapOf(
                        "orderId" to orderId,
                        "cardType" to "SAMSUNG",
                        "cardNo" to "1234-5678-1234-5678",
                    ),
                    authHeaders(),
                ),
                mapResponseType,
            )

        private fun authHeaders(
            loginId: String = 기본_로그인_ID,
            rawPassword: String = 기본_비밀번호,
        ): HttpHeaders {
            val headers = HttpHeaders()
            headers.set("X-Loopers-LoginId", loginId)
            headers.set("X-Loopers-LoginPw", rawPassword)
            return headers
        }

        companion object {
            private const val MAX_REQUEST_ATTEMPTS = 8
            private val NETWORK: Network = Network.newNetwork()

            @JvmStatic
            @Container
            private val PG_MYSQL: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.0"))
                .withNetwork(NETWORK)
                .withNetworkAliases("pg-mysql")
                .withDatabaseName("paymentgateway")
                .withUsername("test")
                .withPassword("test")
                .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_general_ci",
                    "--skip-character-set-client-handshake",
                )

            @JvmStatic
            @Container
            private val PG_REDIS: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:latest"))
                .withNetwork(NETWORK)
                .withNetworkAliases("pg-redis")
                .withExposedPorts(6379)

            @JvmStatic
            @Container
            private val PG_SIMULATOR: GenericContainer<*> = GenericContainer(DockerImageName.parse("eclipse-temurin:21-jre"))
                .withNetwork(NETWORK)
                .withCopyFileToContainer(MountableFile.forHostPath(resolvePgSimulatorJar()), "/app/app.jar")
                .withExposedPorts(8082)
                .withCommand(
                    "java",
                    "-jar",
                    "/app/app.jar",
                    "--spring.profiles.active=test",
                    // pg-simulator 전용 인프라(같은 Testcontainers 네트워크의 별도 MySQL/Redis)로 연결을 덮어쓴다.
                    "--datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://pg-mysql:3306/paymentgateway",
                    "--datasource.mysql-jpa.main.username=test",
                    "--datasource.mysql-jpa.main.password=test",
                    "--datasource.redis.master.host=pg-redis",
                    "--datasource.redis.master.port=6379",
                    "--datasource.redis.replicas[0].host=pg-redis",
                    "--datasource.redis.replicas[0].port=6379",
                )
                .dependsOn(PG_MYSQL, PG_REDIS)
                .waitingFor(Wait.forLogMessage(".*Started PaymentGatewayApplication.*", 1))
                .withStartupTimeout(Duration.ofSeconds(180))

            @JvmStatic
            @DynamicPropertySource
            fun pgProperties(registry: DynamicPropertyRegistry) {
                registry.add("payment.pg-simulator.base-url") {
                    "http://${PG_SIMULATOR.host}:${PG_SIMULATOR.getMappedPort(8082)}"
                }
            }

            // build.gradle.kts 의 `tasks.test` 에서 주입한 pg-simulator build/libs 경로에서 실행 가능한 bootJar 를 찾는다.
            // (`-plain.jar` 은 의존성 미포함 일반 jar 이므로 제외한다.)
            private fun resolvePgSimulatorJar(): Path {
                val libsPath = System.getProperty("pg.simulator.libs")
                    ?: error("pg.simulator.libs 시스템 프로퍼티가 없습니다. build.gradle.kts 의 tasks.test 설정을 확인하세요.")
                val jar = File(libsPath)
                    .listFiles { file -> file.name.endsWith(".jar") && !file.name.endsWith("-plain.jar") }
                    ?.maxByOrNull { it.length() }
                    ?: error("pg-simulator bootJar 를 찾을 수 없습니다: $libsPath (먼저 :apps:pg-simulator:bootJar 빌드 필요)")
                return jar.toPath()
            }
        }
    }
