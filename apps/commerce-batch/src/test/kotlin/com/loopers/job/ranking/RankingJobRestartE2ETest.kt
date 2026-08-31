package com.loopers.job.ranking

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.JobRepositoryTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ResultSetExtractor
import org.springframework.test.context.TestPropertySource
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import java.util.HexFormat

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = [
    "spring.batch.job.name=${WeeklyRankingJobConfig.JOB_NAME}",
    "spring.batch.job.enabled=false",
])
class RankingJobRestartE2ETest @Autowired constructor(
    private val launcher: JobLauncherTestUtils,
    private val repository: JobRepositoryTestUtils,
    private val jdbc: JdbcTemplate,
    @param:Qualifier(WeeklyRankingJobConfig.JOB_NAME) private val job: Job,
) {
    @BeforeEach
    fun seedPublicFixture() {
        repository.removeJobExecutions()
        launcher.job = job
        jdbc.execute("drop table if exists weekly_ranking")
        jdbc.execute("drop table if exists ranking_applied_event")
        jdbc.execute("drop table if exists ranking_source")
        jdbc.execute("create table ranking_source (seq bigint primary key,event_id varchar(80) not null,product_id bigint not null,score_delta bigint not null,occurred_at timestamp not null)")
        jdbc.execute("create table ranking_applied_event (snapshot_id varchar(40) not null,event_id varchar(80) not null,primary key(snapshot_id,event_id))")
        jdbc.execute("create table weekly_ranking (snapshot_id varchar(40) not null,product_id bigint not null,score bigint not null,ranking_position int not null,primary key(snapshot_id,product_id))")
        jdbc.update("insert into ranking_source values (1,'event-01',101,100,'2026-08-17 00:00:00'),(2,'event-02',202,70,'2026-08-18 00:00:00'),(3,'event-02',202,70,'2026-08-18 00:00:00')")
    }

    @Test
    fun failedExecutionRestartsSameInstanceFromCheckpointTwoAndMatchesChecksums() {
        val failed = launcher.launchJob(parameters("2026-08-17", true))
        assertThat(failed.status).isEqualTo(BatchStatus.FAILED)
        assertThat(failed.stepExecutions.single().executionContext.getInt("reader.index")).isEqualTo(2)
        assertThat(failed.stepExecutions.single().writeCount).isEqualTo(2)

        val restarted = launcher.launchJob(parameters("2026-08-17", false))
        assertThat(restarted.status).isEqualTo(BatchStatus.COMPLETED)
        assertThat(restarted.jobInstance.instanceId).isEqualTo(failed.jobInstance.instanceId)
        assertThat(restarted.id).isNotEqualTo(failed.id)
        assertThat(restarted.stepExecutions.single().executionContext.getInt("reader.index")).isEqualTo(3)
        assertThat(checksum(sourceRows())).isEqualTo(SOURCE_SHA)
        assertThat(resultRows()).isEqualTo("snapshot-2026w34\t101\t100\t1\nsnapshot-2026w34\t202\t70\t2\n")
        assertThat(checksum(resultRows())).isEqualTo(RESULT_SHA)
        assertThat(jdbc.queryForObject("select count(*) from ranking_applied_event", Int::class.java)).isEqualTo(2)
    }

    @Test
    fun changedIdentifyingParameterCreatesFreshInstanceWithSameResult() {
        val first = launcher.launchJob(parameters("2026-08-17", false))
        jdbc.update("delete from weekly_ranking")
        jdbc.update("delete from ranking_applied_event")
        val fresh = launcher.launchJob(parameters("2026-08-18", false))
        assertThat(fresh.jobInstance.instanceId).isNotEqualTo(first.jobInstance.instanceId)
        assertThat(checksum(resultRows())).isEqualTo(RESULT_SHA)
    }

    private fun parameters(periodStart: String, injectFailure: Boolean): JobParameters =
        JobParametersBuilder().addString("periodStart", periodStart, true)
            .addString("injectFailure", injectFailure.toString(), false).toJobParameters()

    private fun sourceRows(): String = jdbc.query(
        "select seq,event_id,product_id,score_delta,occurred_at from ranking_source order by seq",
        ResultSetExtractor { rs ->
        buildString {
            while (rs.next()) {
                append(rs.getLong(1)).append('\t').append(rs.getString(2)).append('\t')
                    .append(rs.getLong(3)).append('\t').append(rs.getLong(4)).append('\t')
                    .append(rs.getTimestamp(5).toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))).append('\n')
            }
        }
    })!!

    private fun resultRows(): String = jdbc.query(
        "select snapshot_id,product_id,score,ranking_position from weekly_ranking order by ranking_position",
        ResultSetExtractor { rs ->
        buildString {
            while (rs.next()) {
                append(rs.getString(1)).append('\t').append(rs.getLong(2)).append('\t')
                    .append(rs.getLong(3)).append('\t').append(rs.getInt(4)).append('\n')
            }
        }
    })!!

    private fun checksum(text: String) = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8)),
    )

    companion object {
        const val SOURCE_SHA = "d80a24248fd42f959bddc497efa65afd0d3f1cab420933800140b3027bac98e1"
        const val RESULT_SHA = "cd34a2cb816c288cd704e7fdf27784c01b642b571288ec4f1a854b7a1a4a48e4"
    }
}
