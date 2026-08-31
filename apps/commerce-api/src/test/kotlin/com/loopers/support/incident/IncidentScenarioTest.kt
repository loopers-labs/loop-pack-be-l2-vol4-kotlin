package com.loopers.support.incident

import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.payment.PaymentGateway
import com.loopers.domain.payment.PaymentIntent
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "management.server.port=0",
        "management.endpoints.web.exposure.include=health,prometheus",
        "management.endpoint.prometheus.access=read-only",
        "management.prometheus.metrics.export.enabled=true",
        "looppak.incident=checkout-latency-a",
    ],
)
@Import(IncidentScenarioTest.Config::class)
class IncidentScenarioTest @Autowired constructor(
    private val payments: PaymentFacade,
    private val pg: CheckoutLatencyAIncidentFixture,
    private val cleanup: DatabaseCleanUp,
) {
    @LocalManagementPort private var managementPort = 0
    private val http = RestTemplate()

    @AfterEach
    fun clean() { cleanup.truncateAllTables(); pg.reset() }

    @Test
    fun baseline_is_10_of_10() {
        val snapshot = run(false)
        assertThat(snapshot.healthUp).isEqualTo(10)
        assertThat(snapshot.terminal).isEqualTo(10)
        assertThat(snapshot.unknown).isZero()
        assertThat(snapshot.providerEffects).isEqualTo(10)
        writeRaw("baseline", snapshot)
    }

    @Test
    fun fault_keeps_health_up_but_breaks_business_sli() {
        val snapshot = run(true)
        assertThat(snapshot.healthUp).isEqualTo(10)
        assertThat(snapshot.terminal).isEqualTo(7)
        assertThat(snapshot.unknown).isEqualTo(3)
        assertThat(snapshot.providerEffects).isEqualTo(10)
        writeRaw("fault", snapshot)
    }

    @Test
    fun recovery_reconciles_10_of_10() {
        val fault = run(true)
        val dispatchesBeforeRecovery = pg.dispatches
        val intents = fault.intents.map {
            if (it.status == PaymentIntent.Status.UNKNOWN) payments.reconcile(it.id) else it
        }
        val recovered = observe(intents)
        assertThat(recovered.healthUp).isEqualTo(10)
        assertThat(recovered.terminal).isEqualTo(10)
        assertThat(recovered.unknown).isZero()
        assertThat(recovered.providerEffects).isEqualTo(10)
        assertThat(pg.dispatches).isEqualTo(dispatchesBeforeRecovery)
        writeRaw("recovery", recovered)
    }

    private fun run(fault: Boolean): Snapshot {
        if (fault) pg.enableFault()
        val intents = mutableListOf<PaymentIntent>()
        var healthUp = 0
        var health = ""
        repeat(10) { index ->
            val ordinal = index + 1
            intents += payments.dispatch(ordinal.toLong(), "attempt-%02d".format(ordinal), 1_000)
            val response = get("/actuator/health")
            health = response.body.orEmpty()
            if (response.statusCode.is2xxSuccessful && health.contains("\"status\":\"UP\"")) healthUp++
        }
        return summarize(intents, healthUp, health, get("/actuator/prometheus").body.orEmpty())
    }

    private fun observe(intents: List<PaymentIntent>): Snapshot {
        var healthUp = 0
        var health = ""
        repeat(10) {
            val response = get("/actuator/health")
            health = response.body.orEmpty()
            if (response.statusCode.is2xxSuccessful && health.contains("\"status\":\"UP\"")) healthUp++
        }
        return summarize(intents, healthUp, health, get("/actuator/prometheus").body.orEmpty())
    }

    private fun summarize(intents: List<PaymentIntent>, healthUp: Int, health: String, prom: String): Snapshot {
        val unknown = intents.count { it.status == PaymentIntent.Status.UNKNOWN }
        return Snapshot(healthUp, intents.size - unknown, unknown, pg.providerEffects(), intents.toList(), health, prom)
    }

    private fun get(path: String): ResponseEntity<String> =
        http.getForEntity("http://localhost:$managementPort$path", String::class.java)

    private fun writeRaw(marker: String, snapshot: Snapshot) {
        val evidence = repositoryRoot().resolve("evidence/week10")
        Files.createDirectories(evidence)
        Files.writeString(evidence.resolve("$marker-health.json"), snapshot.rawHealth, StandardCharsets.UTF_8)
        Files.writeString(evidence.resolve("$marker.prom"), snapshot.rawPrometheus, StandardCharsets.UTF_8)
        assertThat(snapshot.rawHealth).isNotBlank()
        assertThat(snapshot.rawPrometheus).containsPattern("(?m)^(# HELP|# TYPE|[a-zA-Z_:][a-zA-Z0-9_:]*)")
    }

    private fun repositoryRoot(): Path {
        var current: Path? = Path.of("").toAbsolutePath()
        while (current != null && !Files.exists(current.resolve(".git"))) current = current.parent
        return checkNotNull(current) { "repository root not found" }
    }

    data class Snapshot(
        val healthUp: Int, val terminal: Int, val unknown: Int, val providerEffects: Int,
        val intents: List<PaymentIntent>, val rawHealth: String, val rawPrometheus: String,
    )

    @TestConfiguration
    class Config {
        @Bean fun checkoutLatencyAIncidentFixture() = CheckoutLatencyAIncidentFixture()
        @Bean @Primary fun incidentPaymentGateway(fixture: CheckoutLatencyAIncidentFixture): PaymentGateway = fixture
    }
}
