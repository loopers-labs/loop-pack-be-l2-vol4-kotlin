# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Setup

```shell
# pre-commit hook 설치 (ktlint 자동 검사)
make init

# 인프라 실행 (MySQL, Redis, Kafka)
docker compose -f ./docker/infra-compose.yml up -d

# 모니터링 실행 (Prometheus + Grafana)
# 접속: http://localhost:3000 / admin:admin
docker compose -f ./docker/monitoring-compose.yml up -d
```

## Commands

```shell
# 전체 빌드
./gradlew build

# 특정 모듈 테스트
./gradlew :apps:commerce-api:test

# 단일 테스트 클래스 실행
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.example.ExampleModelTest"

# 단일 테스트 메서드 실행
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.example.ExampleModelTest.createsExampleModel_whenNameAndDescriptionAreProvided"

# ktlint 검사
./gradlew ktlintCheck

# ktlint 자동 수정
./gradlew ktlintFormat
```

## 기술 스택 및 버전

| 항목 | 버전 |
|------|------|
| JDK | 21 (Gradle 실행 및 컴파일 모두 JDK 21 필요) |
| Kotlin | 2.0.20 |
| Spring Boot | 3.4.4 |
| Spring Cloud | 2024.0.1 |
| Spring Dependency Management | 1.1.7 |
| ktlint (plugin) | 12.1.2 |
| ktlint | 1.0.1 |
| springdoc-openapi | 2.7.0 |
| Mockito | 5.14.0 |
| mockito-kotlin | 5.4.0 |
| springmockk | 4.0.2 |
| instancio-junit | 5.0.2 |

## 모듈 구조

```
Root
├── apps/                    # 실행 가능한 SpringBootApplication
│   ├── commerce-api         # REST API 서버 (Web + JPA + Redis)
│   ├── commerce-batch       # 배치 서버 (Batch + JPA + Redis)
│   └── commerce-streamer    # 스트리밍 서버 (Web + JPA + Redis + Kafka)
├── modules/                 # 도메인에 독립적인 재사용 가능한 설정
│   ├── jpa                  # Spring Data JPA + QueryDSL + MySQL + Testcontainers
│   ├── redis                # Spring Data Redis + Testcontainers
│   └── kafka                # Spring Kafka + Testcontainers
└── supports/                # 부가 기능 add-on
    ├── jackson              # Jackson Kotlin/JSR310 설정
    ├── logging              # Logback + Slack Appender + Micrometer Tracing
    └── monitoring           # Actuator + Micrometer Prometheus
```

### 모듈 의존 관계

```
commerce-api      → jpa, redis, jackson, logging, monitoring
commerce-batch    → jpa, redis, jackson, logging, monitoring
commerce-streamer → jpa, redis, kafka, jackson, logging, monitoring
```

## 레이어 아키텍처 (apps 내부)

```
interfaces/api/    ← Controller, ApiSpec(Swagger 분리), Dto
application/       ← Facade (여러 Service 조합, 유즈케이스 조율)
domain/            ← Service (비즈니스 로직), Repository 인터페이스, Model (Entity)
infrastructure/    ← JpaRepository, RepositoryImpl
support/           ← CoreException, ErrorType
```

호출 방향: `Controller → Facade → Service → Repository(interface) ← RepositoryImpl`

### 주요 패턴

- **Controller**: `XxxV1Controller`는 `XxxV1ApiSpec` 인터페이스를 구현. Swagger 어노테이션은 ApiSpec에만 작성
- **응답 형식**: 모든 API 응답은 `ApiResponse<T>`로 래핑
- **예외**: `CoreException(errorType, customMessage)`로 통일. `ErrorType` enum으로 HTTP 상태코드 매핑
- **Entity**: `BaseEntity` 상속 (id 자동 생성 포함)
- **DB**: 운영/개발은 MySQL, 테스트는 Testcontainers MySQL (H2 미사용)
- **QueryDSL**: kapt로 Q클래스 생성

## 테스트 구조

| 종류 | 클래스명 패턴 | 설명 |
|------|-------------|------|
| 단위 테스트 | `XxxModelTest` | 순수 JUnit5, 스프링 컨텍스트 없음 |
| 통합 테스트 | `XxxServiceIntegrationTest` | `@SpringBootTest` + Testcontainers |
| E2E 테스트 | `XxxV1ApiE2ETest` | `@SpringBootTest(RANDOM_PORT)` + TestRestTemplate |

- `@Nested` inner class로 시나리오 그룹화
- `arrange / act / assert` 주석으로 단계 구분
- `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()`로 테스트 격리

## 인프라 접속 정보 (local)

| 서비스 | 포트 | 계정 |
|--------|------|------|
| MySQL | 3306 | application / application / DB: loopers |
| Redis Master | 6379 | - |
| Redis Readonly | 6380 | - |
| Kafka | 19092 | - |
| Kafka UI | 9099 | - |


## 개발 규칙
### 진행 Workflow - 증강 코딩
- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지** : AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.

### 개발 Workflow - TDD (Red > Green > Refactor)
- 모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)
#### 1. Red Phase : 실패하는 테스트 먼저 작성
- 요구사항을 만족하는 기능 테스트 케이스 작성
- 테스트 예시
#### 2. Green Phase : 테스트를 통과하는 코드 작성
- Red Phase 의 테스트가 모두 통과할 수 있는 코드 작성
- 오버엔지니어링 금지
#### 3. Refactor Phase : 불필요한 코드 제거 및 품질 개선
- 불필요한 private 함수 지양, 객체지향적 코드 작성
- unused import 제거
- 성능 최적화
- 모든 테스트 케이스가 통과해야 함

## 주의사항
### 1. Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현을 하지 말 것
- null-safety 하지 않게 코드 작성하지 말 것 (Java 의 경우, Optional 을 활용할 것)
- println 코드 남기지 말 것

### 2. Recommendation
- 실제 API 를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API 의 경우, `.http/**.http` 에 분류해 작성

### 3. Priority
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지