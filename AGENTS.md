# Repository Guidelines

## Agent Operating Principles

For TDD work, use `$kent-beck-tdd`: draft a concise JUnit test list first, get user confirmation, write one failing test, implement the simplest passing code, then refactor. Keep production code justified by an active test or concrete use case.

Apply YAGNI first. Build only what is needed now; avoid speculative layers, generic abstractions, and premature extension points. If extension is backed by clear requirements, keep the design flexible and explain the tradeoff.

Consider SOLID in every change. Introduce design patterns only when they simplify current code, clarify responsibilities, or reduce real duplication. Propose benefits and costs before implementing one.

## Version & Documentation Policy

Before coding against Java, Kotlin, Spring, Gradle plugins, or libraries, check versions in `gradle.properties` and `build.gradle.kts`. Verify version-sensitive behavior with official vendor docs first. Prefer official docs and mature engineering references from major tech companies over personal blog posts. Note important version assumptions in PRs or concise comments.

## Project Structure

This is a Kotlin/Spring Boot multi-module Gradle project. Apps live under `apps/`; active modules are `commerce-api`, `commerce-streamer`, and `commerce-batch`. Shared infrastructure is under `modules/`; add-ons are under `supports/`. Use Gradle paths: `src/main/kotlin`, `src/main/resources`, `src/test/kotlin`, and test fixtures. HTTP examples are in `http/`; local infrastructure is in `docker/`.

## Commands

- `make init`: installs hooks; pre-commit runs `./gradlew ktlintCheck`.
- `./gradlew build`: compiles modules and runs tests.
- `./gradlew test`: runs tests.
- `./gradlew ktlintCheck` / `./gradlew ktlintFormat`: check/apply Kotlin formatting.
- `./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local'`: starts API.
- `docker-compose -f ./docker/infra-compose.yml up`: starts dependencies.

## Style & Architecture

Use Kotlin with Java 21. Follow `.editorconfig`: IntelliJ Kotlin style, 130-character line limit, trailing commas, and no wildcard imports. Keep packages under `com.loopers`. Preserve `interfaces`, `application`, `domain`, and `infrastructure` boundaries.

## Testing Guidelines

Tests run with JUnit 5, the `test` Spring profile, and `Asia/Seoul` timezone. Use Spring Boot Test, MockK/Mockito, Instancio, and Testcontainers as existing tests do. Name unit tests `*Test.kt`, integration tests `*IntegrationTest.kt`, and E2E tests `*E2ETest.kt`. Run focused tests with `./gradlew :apps:commerce-api:test --tests '*ExampleServiceIntegrationTest'`.

## Commit & Pull Request Guidelines

For Git and PR work, use `$loopers-pr-workflow`. Work from the user's fork (`origin`) and target final review PRs to `loopers-labs/loop-pack-be-l2-vol4-kotlin`, base `shoeone96`. Use Conventional Commit-style prefixes, for example `chore: PR 템플릿 통일 및 개선`; keep messages short and scoped. Follow `.github/pull_request_template.md` and include test results when relevant.

## Configuration & Security

Do not commit secrets. Keep `http/http-client.env.json` non-sensitive.
