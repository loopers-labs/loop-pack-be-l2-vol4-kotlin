# Repository Guidelines

Rules are ordered by priority. When two instructions appear to conflict, follow the higher-priority section first.

## Table of Contents

1. [Required Rules](#required-rules)
2. [Design Principles](#design-principles)
3. [Code Organization](#code-organization)
4. [Development Workflow](#development-workflow)
5. [Testing Guidelines](#testing-guidelines)
6. [Project Structure](#project-structure)
7. [Command Reference](#command-reference)
8. [Git and Pull Requests](#git-and-pull-requests)
9. [Configuration and Security Files](#configuration-and-security-files)

## Required Rules

Do not commit secrets. Keep `http/http-client.env.json` non-sensitive.

Use Kotlin with Java 21. Follow `.editorconfig`: IntelliJ Kotlin style, 130-character line limit, trailing commas, and no wildcard imports. Keep packages under `com.loopers`.

Preserve the architectural boundaries of `interfaces`, `application`, `domain`, and `infrastructure`. Do not move domain rules into interface or infrastructure code to make a change easier.

For expected business/application failures thrown from our code, use the custom exception model in `supports/error/src/main/kotlin/com/loopers/support/error/CoreException.kt`: `BadRequestException`, `UnauthorizedException`, `ForbiddenException`, `NotFoundException`, `ConflictException`, or `InternalServerException`. Throw the HTTP-semantic wrapper with a domain error code, e.g. `throw ConflictException(AccountErrorCode.DUPLICATE_EMAIL)`. This is the path consumed by `supports/web/src/main/kotlin/com/loopers/interfaces/api/ApiControllerAdvice.kt`, which maps exception subclasses to HTTP status, and `ApiResponse.fail`, which reads `exception.errorCode.code` and `exception.message`.

Define error codes per domain by importing `com.loopers.support.error.ErrorCode` from the `supports/error` module and implementing it with a domain enum. Follow `apps/commerce-api/src/main/kotlin/com/loopers/account/domain/error/AccountErrorCode.kt`: `enum class XxxErrorCode(override val message: String) : ErrorCode`, with `override val code` using the domain prefix plus enum name, such as `ACCOUNT:$name`.

Do not introduce ad hoc `RuntimeException`, app-local `CoreException`, `ErrorType`-based models, a new `ErrorCode` interface, or duplicate common error-code abstractions for new business/application failures. If a framework requires its own exception type, wrap a `CoreException` as `AccountAuthenticationException(coreException: CoreException)` does.

Before coding against Java, Kotlin, Spring, Gradle plugins, or libraries, check versions in `gradle.properties` and `build.gradle.kts`. Verify version-sensitive behavior with official vendor docs first. Prefer official docs and mature engineering references from major tech companies over personal blog posts.

## Design Principles

Apply YAGNI first. Build only what is needed now; avoid speculative layers, generic abstractions, and premature extension points. If extension is backed by clear requirements, keep the design flexible and explain the tradeoff.

Consider SOLID in every change. Introduce design patterns only when they simplify current code, clarify responsibilities, or reduce real duplication. Propose benefits and costs before implementing one.

Keep production code justified by an active test or concrete use case.

## Code Organization

Place domain error-code enums in the owning domain module, not in application or interface layers.

Keep highly cohesive, owner-specific types in the same `.kt` file when they have a single owner. Follow the existing pattern in `apps/commerce-api/src/main/kotlin/com/loopers/account/application/AccountService.kt`, where `AccountCreateCommand`, `AccountAuthenticateCommand`, and account info DTOs live below `AccountService`, and in `apps/commerce-api/src/main/kotlin/com/loopers/account/infrastructure/security/AccountHeaderAuthenticationFilter.kt`, where authentication header/attribute objects and `AccountPrincipal` live with the filter.

Split types into separate files when they are reused across owners or represent stable domain/infrastructure concepts, such as `AccountErrorCode.kt`, value objects under `domain/vo`, or Redis configuration properties.

## Development Workflow

For TDD work, use `$kent-beck-tdd`: draft a concise JUnit test list first, get user confirmation, write one failing test, implement the simplest passing code, then refactor.

Note important version assumptions in PRs or concise comments when they affect implementation choices.

## Testing Guidelines

Tests run with JUnit 5, the `test` Spring profile, and `Asia/Seoul` timezone. Use Spring Boot Test, MockK/Mockito, Instancio, and Testcontainers as existing tests do.

Name unit tests `*Test.kt`, integration tests `*IntegrationTest.kt`, and E2E tests `*E2ETest.kt`.

Run focused tests with `./gradlew :apps:commerce-api:test --tests '*ExampleServiceIntegrationTest'`.

## Project Structure

This is a Kotlin/Spring Boot multi-module Gradle project. Apps live under `apps/`; active modules are `commerce-api`, `commerce-streamer`, and `commerce-batch`.

Shared infrastructure is under `modules/`; add-ons are under `supports/`. Use Gradle paths: `src/main/kotlin`, `src/main/resources`, `src/test/kotlin`, and test fixtures.

HTTP examples are in `http/`; local infrastructure is in `docker/`.

## Command Reference

- `make init`: installs hooks; pre-commit runs `./gradlew ktlintCheck`.
- `./gradlew build`: compiles modules and runs tests.
- `./gradlew test`: runs tests.
- `./gradlew ktlintCheck` / `./gradlew ktlintFormat`: check/apply Kotlin formatting.
- `./gradlew :apps:<app>:bootRun --args='--spring.profiles.active=local'`: starts API. Always include `--args` — apps no longer hardcode a default `spring.profiles.active`, so omitting the flag leaves datasource and other profile-bound config unset and the app fails to start. IDE Run Configurations must set the profile explicitly.
- `docker-compose -f ./docker/infra-compose.yml up`: starts dependencies.

## Git and Pull Requests

For Git and PR work, use `$loopers-pr-workflow`. Work from the user's fork (`origin`) and target final review PRs to `loopers-labs/loop-pack-be-l2-vol4-kotlin`, base `shoeone96`.

Use Conventional Commit-style prefixes, for example `chore: PR 템플릿 통일 및 개선`; keep messages short and scoped.

Follow `.github/pull_request_template.md` and include test results when relevant.

## Configuration and Security Files

Keep runtime configuration, local infrastructure, and HTTP client examples out of business logic changes unless the task explicitly requires them.
