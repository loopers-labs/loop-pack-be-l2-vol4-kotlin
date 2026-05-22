# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

Kotlin 2.0.20 on JDK 21, Spring Boot 3.4.4, Gradle (Kotlin DSL) multi-module build. Tests run on JUnit 5 with springmockk, mockito-kotlin, Instancio, and Testcontainers. Time zone is fixed to `Asia/Seoul` in app `main` and to `UTC` for Hibernate JDBC storage.

## Commands

```shell
make init                                            # install pre-commit hook (runs ktlintCheck)
docker-compose -f ./docker/infra-compose.yml up      # local MySQL (3306), Redis master/replica (6379/6380), Kafka (19092), Kafka UI (9099)
docker-compose -f ./docker/monitoring-compose.yml up # Prometheus + Grafana (localhost:3000, admin/admin)

./gradlew build                                      # full build
./gradlew :apps:commerce-api:bootRun                 # run an app (commerce-api | commerce-batch | commerce-streamer)
./gradlew test                                       # all tests
./gradlew :apps:commerce-api:test                    # tests for one module
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ExampleV1ApiE2ETest"           # one class
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ExampleV1ApiE2ETest.Get.*"     # one nested class
./gradlew ktlintCheck                                # lint (pre-commit runs this)
./gradlew ktlintFormat                               # auto-fix lint
./gradlew jacocoTestReport                           # coverage XML at build/reports/jacoco/test/jacocoTestReport.xml
```

Test JVM is forced to single-fork (`maxParallelForks = 1`), `user.timezone=Asia/Seoul`, `spring.profiles.active=test`. Tests using JPA spin up a real MySQL 8.0 via Testcontainers (see `modules/jpa` test fixtures) — Docker must be running.

## Architecture

### Module hierarchy

The `build.gradle.kts` rules enforce the hierarchy in `settings.gradle.kts`:

- `apps/*` — `SpringBootApplication` entrypoints. Produce `bootJar`; plain `jar` is disabled. Currently: `commerce-api` (web), `commerce-batch` (Spring Batch), `commerce-streamer` (Kafka).
- `modules/*` — reusable infra config (`jpa`, `redis`, `kafka`). Expose `@Configuration` and shared base types via `api(...)` so apps inherit Spring starters transitively. Provide Testcontainers + cleanup utilities via Gradle `testFixtures` (consumed with `testImplementation(testFixtures(project(":modules:jpa")))`).
- `supports/*` — cross-cutting add-ons (`jackson`, `logging`, `monitoring`). Apps wire them in by adding the module dependency and importing the module's `*.yml` from `application.yml`.

The root `build.gradle.kts` disables tasks on the `apps`/`modules`/`supports` container projects themselves — you cannot run a task at that level, only on leaf subprojects.

### Configuration composition

Apps load module configs through Spring's `spring.config.import` (see `apps/commerce-api/src/main/resources/application.yml` importing `jpa.yml`, `redis.yml`, `logging.yml`, `monitoring.yml`). Each module ships its own YAML with profile slices (`local`, `test`, `dev`, `qa`, `prd`). Profile `local` is the default for apps; tests run under `test`.

MySQL datasource is built manually in `modules/jpa/.../DataSourceConfig.kt` from `datasource.mysql-jpa.main.*` — not from `spring.datasource.*`. The `MySqlTestContainersConfig` sets those `datasource.mysql-jpa.main.*` system properties at class init, so any test that loads it gets a real MySQL transparently.

### Layered package layout (inside `commerce-api`)

```
interfaces.api.<feature>      Controllers, request/response DTOs, OpenAPI ApiSpec interfaces
application.<feature>         Facade + cross-domain orchestration (Info objects, no JPA leakage)
domain.<feature>              Entities (extend BaseEntity), services, repository interfaces
infrastructure.<feature>      Repository implementations (JPA + QueryDSL impls of domain interfaces)
support.error                 CoreException + ErrorType
```

The flow is `Controller → Facade → Service → Repository interface → RepositoryImpl → JpaRepository`. The repository interface lives in `domain` and its implementation in `infrastructure` — keep domain free of JPA imports. See `example/` for a canonical end-to-end slice.

### Conventions worth knowing

- **Errors:** throw `CoreException(ErrorType, customMessage)`. `ApiControllerAdvice` maps it (and common Spring binding errors) to a standardized `ApiResponse` envelope (`meta.result` + `errorCode` + `message`). Add new error categories by extending `ErrorType` rather than ad-hoc HTTP status mapping.
- **Entities:** extend `modules/jpa`'s `BaseEntity` for `id`/`createdAt`/`updatedAt`/`deletedAt`. `delete()`/`restore()` are idempotent (soft-delete via `deletedAt`). Override `guard()` to add invariants enforced at `@PrePersist`/`@PreUpdate`. Avoid `data class` for JPA entities (see `.coderabbit.yaml` review rules).
- **API response:** always wrap responses with `ApiResponse.success(...)`. Controllers implement an `*ApiSpec` interface that carries OpenAPI annotations (Swagger UI at `/swagger-ui.html`, disabled in `prd`).
- **Batch jobs:** each job is gated by `@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = JOB_NAME)` so only the requested job is wired (see `DemoJobConfig`). Run a specific job by passing `--spring.batch.job.name=<jobName>`.
- **Kafka consumers:** use the shared `KafkaConfig.BATCH_LISTENER` container factory in `modules/kafka` (manual ack, batch mode, concurrency 3). Always call `acknowledgment.acknowledge()` after processing.
- **QueryDSL:** every app applies `kapt("com.querydsl:querydsl-apt::jakarta")`. Generated Q-types live under `build/generated/source/kapt/`.

### Testing patterns

- E2E API tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `TestRestTemplate` (see `ExampleV1ApiE2ETest`).
- Inject `DatabaseCleanUp` (from `modules/jpa` test fixtures) and call `truncateAllTables()` in `@AfterEach`; `RedisCleanUp` is the Redis equivalent.
- Batch job tests use `@SpringBatchTest` + `@TestPropertySource(properties = ["spring.batch.job.name=<JOB>"])` and launch via `JobLauncherTestUtils`.
- DDL is auto-created (`ddl-auto: create`) in `local`/`test` — don't write migrations for local schema changes; just update the entity.
- Junit is used as a testing framework

## Lint

ktlint runs via the `org.jlleitschuh.gradle.ktlint` plugin (version pinned in `gradle.properties`). The pre-commit hook (`.githooks/pre-commit`, installed by `make init`) runs `./gradlew ktlintCheck` — fix violations with `ktlintFormat` rather than bypassing. `.editorconfig` disables a few ktlint standards (`package-name`, `function-signature`, `import-ordering`, `indent`) and caps line length at 130 (off in `*Test.kt`).
