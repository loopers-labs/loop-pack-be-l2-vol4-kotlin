package com.loopers.e2e

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RankingEndToEndTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    private val mysql = MySQLContainer(DockerImageName.parse("mysql:8.0"))
        .withDatabaseName("loopers")
        .withUsername("application")
        .withPassword("application")
    private val redis = GenericContainer(DockerImageName.parse("redis:7.0"))
        .withExposedPorts(REDIS_PORT)
    private val kafka = ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))

    private lateinit var apiProcess: Process
    private lateinit var streamerProcess: Process
    private lateinit var apiLog: Path
    private lateinit var streamerLog: Path
    private var apiPort: Int = 0
    private var apiManagementPort: Int = 0
    private var streamerPort: Int = 0
    private var streamerManagementPort: Int = 0

    @BeforeAll
    fun setUp() {
        mysql.start()
        redis.start()
        kafka.start()
        createTopics()

        apiPort = availablePort()
        apiManagementPort = availablePort()
        streamerPort = availablePort()
        streamerManagementPort = availablePort()

        val logDirectory = Path.of("build", "e2e-logs").toAbsolutePath()
        Files.createDirectories(logDirectory)
        apiLog = logDirectory.resolve("commerce-api.log")
        streamerLog = logDirectory.resolve("commerce-streamer.log")

        apiProcess = startApplication(
            jarProperty = "commerce.api.jar",
            serverPort = apiPort,
            managementPort = apiManagementPort,
            logFile = apiLog,
            additionalArguments = listOf("--spring.kafka.listener.auto-startup=false"),
        )
        waitUntilHealthy(apiProcess, apiManagementPort, apiLog)

        streamerProcess = startApplication(
            jarProperty = "commerce.streamer.jar",
            serverPort = streamerPort,
            managementPort = streamerManagementPort,
            logFile = streamerLog,
            additionalArguments = listOf(
                "--commerce.ranking.consumer-group=ranking-e2e-${System.nanoTime()}",
            ),
        )
        waitUntilHealthy(streamerProcess, streamerManagementPort, streamerLog)

        // auto.offset.reset=latest이므로 consumer의 partition 할당이 끝난 뒤 행동 이벤트를 발생시킨다.
        Thread.sleep(CONSUMER_ASSIGNMENT_WAIT.toMillis())
    }

    @AfterAll
    fun tearDown() {
        stopProcess(::streamerProcess)
        stopProcess(::apiProcess)
        kafka.stop()
        redis.stop()
        mysql.stop()
    }

    @DisplayName("상품 조회 이벤트가 Kafka와 Redis를 거쳐 실시간 랭킹 API에 반영된다")
    @Test
    fun reflectsProductViewedEventInRankingApi() {
        val brandId = createBrand()
        val productId = createProduct(brandId)

        val productDetail = get("/api/v1/products/$productId")

        assertThat(productDetail.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK)
        assertThat(json(productDetail).path("data").path("productId").asLong()).isEqualTo(productId)

        val ranking = awaitRankedProduct(productId)
        assertThat(ranking.path("productName").asText()).isEqualTo(PRODUCT_NAME)
        assertThat(ranking.path("brandName").asText()).isEqualTo(BRAND_NAME)
        assertThat(ranking.path("rank").asLong()).isEqualTo(1L)
        assertThat(ranking.path("score").asDouble()).isEqualTo(VIEW_SCORE)
    }

    private fun createBrand(): Long {
        val response = post(
            path = "/api-admin/v1/brands",
            body = """
                {
                  "name": "$BRAND_NAME",
                  "description": "ranking e2e brand",
                  "logoImageUrl": "https://image.loopers/ranking-e2e-brand.png"
                }
            """.trimIndent(),
            admin = true,
        )

        assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK)
        return json(response).path("data").path("brandId").asLong()
    }

    private fun createProduct(brandId: Long): Long {
        val response = post(
            path = "/api-admin/v1/products",
            body = """
                {
                  "brandId": $brandId,
                  "name": "$PRODUCT_NAME",
                  "price": 10000,
                  "description": "ranking e2e product",
                  "imageUrl": "https://image.loopers/ranking-e2e-product.png",
                  "quantity": 100
                }
            """.trimIndent(),
            admin = true,
        )

        assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK)
        return json(response).path("data").path("productId").asLong()
    }

    private fun awaitRankedProduct(productId: Long): JsonNode {
        val date = LocalDate.now(RANKING_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE)
        val deadline = System.nanoTime() + RANKING_WAIT.toNanos()
        var lastResponse = ""

        while (System.nanoTime() < deadline) {
            val response = get("/api/v1/rankings?date=$date&page=0&size=20")
            lastResponse = response.body()
            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                val ranking = json(response)
                    .path("data")
                    .path("data")
                    .firstOrNull { it.path("productId").asLong() == productId }
                if (ranking != null) {
                    return ranking
                }
            }
            Thread.sleep(RANKING_POLL_INTERVAL.toMillis())
        }

        fail<Unit>(
            """
            상품 $productId 가 제한 시간 내 랭킹에 반영되지 않았습니다.
            마지막 랭킹 응답: $lastResponse
            commerce-api 로그: ${tail(apiLog)}
            commerce-streamer 로그: ${tail(streamerLog)}
            """.trimIndent(),
        )
        error("unreachable")
    }

    private fun createTopics() {
        val properties = mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
        )
        AdminClient.create(properties).use { adminClient ->
            adminClient.createTopics(
                listOf(
                    NewTopic(CATALOG_TOPIC, TOPIC_PARTITIONS, TOPIC_REPLICATION_FACTOR),
                    NewTopic(ORDER_TOPIC, TOPIC_PARTITIONS, TOPIC_REPLICATION_FACTOR),
                ),
            ).all().get(10, TimeUnit.SECONDS)
        }
    }

    private fun startApplication(
        jarProperty: String,
        serverPort: Int,
        managementPort: Int,
        logFile: Path,
        additionalArguments: List<String>,
    ): Process {
        val jarPath = checkNotNull(System.getProperty(jarProperty)) {
            "Gradle test task에서 $jarProperty 시스템 프로퍼티를 전달해야 합니다."
        }
        val commonArguments = listOf(
            "--spring.profiles.active=test",
            "--spring.main.banner-mode=off",
            "--server.port=$serverPort",
            "--management.server.port=$managementPort",
            "--datasource.mysql-jpa.main.jdbc-url=${mysql.jdbcUrl}",
            "--datasource.mysql-jpa.main.username=${mysql.username}",
            "--datasource.mysql-jpa.main.password=${mysql.password}",
            "--datasource.redis.database=0",
            "--datasource.redis.master.host=${redis.host}",
            "--datasource.redis.master.port=${redis.getMappedPort(REDIS_PORT)}",
            "--datasource.redis.replicas[0].host=${redis.host}",
            "--datasource.redis.replicas[0].port=${redis.getMappedPort(REDIS_PORT)}",
            "--spring.kafka.bootstrap-servers=${kafka.bootstrapServers}",
            "--spring.kafka.admin.properties.bootstrap.servers=${kafka.bootstrapServers}",
            "--commerce.events.catalog-topic=$CATALOG_TOPIC",
            "--commerce.events.order-topic=$ORDER_TOPIC",
        )

        return ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-jar",
            jarPath,
            *commonArguments.toTypedArray(),
            *additionalArguments.toTypedArray(),
        )
            .redirectErrorStream(true)
            .redirectOutput(logFile.toFile())
            .start()
    }

    private fun waitUntilHealthy(
        process: Process,
        managementPort: Int,
        logFile: Path,
    ) {
        val healthUri = URI.create("http://localhost:$managementPort/actuator/health")
        val deadline = System.nanoTime() + APPLICATION_START_WAIT.toNanos()

        while (System.nanoTime() < deadline) {
            check(process.isAlive) {
                "애플리케이션이 시작 중 종료되었습니다.\n${tail(logFile)}"
            }
            runCatching {
                httpClient.send(
                    HttpRequest.newBuilder(healthUri)
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            }.getOrNull()?.let { response ->
                if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                    return
                }
            }
            Thread.sleep(HEALTH_POLL_INTERVAL.toMillis())
        }

        error("애플리케이션이 제한 시간 내 준비되지 않았습니다.\n${tail(logFile)}")
    }

    private fun post(
        path: String,
        body: String,
        admin: Boolean,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(apiUri(path))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
        if (admin) {
            builder.header(ADMIN_HEADER, ADMIN_ID)
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(apiUri(path))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun apiUri(path: String): URI = URI.create("http://localhost:$apiPort$path")

    private fun json(response: HttpResponse<String>): JsonNode = objectMapper.readTree(response.body())

    private fun tail(logFile: Path): String {
        if (!Files.exists(logFile)) {
            return "(로그 파일 없음)"
        }
        return Files.readAllLines(logFile).takeLast(LOG_TAIL_LINES).joinToString("\n")
    }

    private fun availablePort(): Int = ServerSocket(0).use { it.localPort }

    private fun stopProcess(processReference: () -> Process) {
        val process = runCatching(processReference).getOrNull() ?: return
        process.destroy()
        if (!process.waitFor(PROCESS_STOP_WAIT.seconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(PROCESS_STOP_WAIT.seconds, TimeUnit.SECONDS)
        }
    }

    companion object {
        private const val REDIS_PORT = 6379
        private const val TOPIC_PARTITIONS = 3
        private const val TOPIC_REPLICATION_FACTOR: Short = 1
        private const val CATALOG_TOPIC = "catalog-events-ranking-e2e"
        private const val ORDER_TOPIC = "order-events-ranking-e2e"
        private const val ADMIN_HEADER = "X-Loopers-Ldap"
        private const val ADMIN_ID = "loopers.admin"
        private const val BRAND_NAME = "ranking-e2e-brand"
        private const val PRODUCT_NAME = "ranking-e2e-product"
        private const val VIEW_SCORE = 0.05
        private const val LOG_TAIL_LINES = 80
        private val RANKING_ZONE = ZoneId.of("Asia/Seoul")
        private val APPLICATION_START_WAIT = Duration.ofSeconds(60)
        private val CONSUMER_ASSIGNMENT_WAIT = Duration.ofSeconds(3)
        private val RANKING_WAIT = Duration.ofSeconds(45)
        private val RANKING_POLL_INTERVAL = Duration.ofMillis(250)
        private val HEALTH_POLL_INTERVAL = Duration.ofMillis(250)
        private val PROCESS_STOP_WAIT = Duration.ofSeconds(5)
    }
}
