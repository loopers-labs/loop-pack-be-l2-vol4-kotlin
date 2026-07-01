# CLAUDE.md

## Project Overview

Loopers Kotlin Spring Template - 멀티 모듈 Spring Boot 프로젝트

## Tech Stack & Versions

| Category | Technology | Version |
|----------|-----------|---------|
| Language | Kotlin | 2.0.20 |
| JDK | Java | 21 |
| Framework | Spring Boot | 3.4.4 |
| Cloud | Spring Cloud | 2024.0.1 |
| Build | Gradle (Kotlin DSL) | 8.13 |
| DB | MySQL | 8.0 |
| ORM | Spring Data JPA + QueryDSL | - |
| Cache | Spring Data Redis | - |
| Messaging | Spring Kafka (KRaft) | - |
| API Docs | SpringDoc OpenAPI | 2.7.0 |
| Monitoring | Micrometer + Prometheus | - |
| Tracing | Micrometer Tracing (Brave) | - |
| Test | JUnit 5, SpringMockK (4.0.2), Mockito (5.14.0) + mockito-kotlin (5.4.0), Instancio (5.0.2), Testcontainers | - |
| Lint | ktlint | 1.0.1 |
| Coverage | JaCoCo | - |

## Module Structure

```
root
├── apps/                        # Spring Boot 실행 애플리케이션 (bootJar 활성화)
│   ├── commerce-api             # REST API 서버 (port 8080)
│   ├── commerce-batch           # Spring Batch 잡 실행기 (non-web)
│   └── commerce-streamer        # Kafka 컨슈머 서버
├── modules/                     # 재사용 인프라 설정 모듈 (library jar)
│   ├── jpa                      # JPA + QueryDSL + HikariCP + Testcontainers(MySQL)
│   ├── redis                    # Redis Master/Replica + Testcontainers(Redis)
│   └── kafka                    # Kafka Producer/Consumer 설정
├── supports/                    # 부가 기능 모듈 (library jar)
│   ├── jackson                  # ObjectMapper (Kotlin + JSR310)
│   ├── logging                  # Logback + Slack Appender
│   └── monitoring               # Actuator + Prometheus
└── docker/
    ├── infra-compose.yml        # MySQL, Redis, Kafka, Kafka UI
    └── monitoring-compose.yml   # Prometheus, Grafana
```

## Architecture (Layered + Hexagonal)

```
interfaces/   → Inbound Adapter: Controller, Consumer, Request/Response DTO
                + XxxApplicationServicePort 인바운드 포트 (application 구현체와의 연결점)
application/  → Application Service: XxxApplicationServiceAdapter 구현 (도메인 서비스 오케스트레이션),
                Command/Result DTO, 트랜잭션 경계. if/for 같은 복잡 분기 지양.
domain/       → Core + Outbound Port: Entity, VO, Domain Service(XxxService),
                XxxRepositoryPort 인터페이스. Domain Service는 도메인 ↔ Repository 연결 및 도메인 메서드 오케스트레이션.
infrastructure/ → Outbound Adapter: XxxEntity(JPA), XxxJpaRepository, XxxRepositoryAdapter(Port 구현), 외부 API 어댑터
com.loopers.support.error → CoreException, ErrorType (각 앱 모듈 내 공용 에러 패키지)
com.loopers.util          → 레이어에 속하지 않는 순수 유틸 (예: EncryptionUtil). 도메인 규칙 없음.
```

## Rules

상세 규칙(도메인 모델링 / Hexagonal 아키텍처 & 패키지 / 코딩 규칙)은 다음 파일에 분리되어 있으며, Claude Code가 매 실행 시 자동 임포트합니다.

@.claude/rules/domain-modeling.md
@.claude/rules/architecture.md
@.claude/rules/coding.md

## Build & Run

```bash
# 프로젝트 초기화 (git hooks 설정)
make init

# 인프라 실행 (MySQL, Redis, Kafka)
docker-compose -f ./docker/infra-compose.yml up -d

# 모니터링 실행 (Prometheus, Grafana)
docker-compose -f ./docker/monitoring-compose.yml up -d

# 빌드
./gradlew build

# 테스트
./gradlew test

# ktlint 검사
./gradlew ktlintCheck

# ktlint 자동 포맷
./gradlew ktlintFormat
```

## Test Configuration

- JVM Timezone: `Asia/Seoul`
- Active Profile: `test`
- Testcontainers: MySQL, Redis, Kafka (자동 프로비저닝)
- Test fixtures: `modules/jpa`, `modules/redis`에 testFixtures 제공
- 테스트 파일의 max_line_length 제한 없음

## Configuration Profiles

- **local**: 로컬 Docker 서비스 기본값 사용
- **test**: Testcontainers 자동 프로비저닝
- **dev / qa / prd**: 환경변수 기반 설정

## Key Environment Variables

```
MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PWD
REDIS_MASTER_HOST, REDIS_MASTER_PORT
REDIS_REPLICA_1_HOST, REDIS_REPLICA_1_PORT
BOOTSTRAP_SERVERS (Kafka)
```

## Code Style

- ktlint (IntelliJ IDEA style)
- Max line length: 130 (테스트 파일 제외)
- Trailing comma 허용
- Star import 비활성화
- Pre-commit hook으로 ktlint 자동 검사

## Conventions

- `apps/` 모듈만 bootJar 생성 (실행 가능 JAR)
- `modules/`, `supports/`는 library jar만 생성
- Management 엔드포인트: port 8081 (`/health`, `/prometheus`)
- Kafka consumer: manual ACK 모드
- JPA: open-in-view 비활성화, batch fetch size 100
- JPA 연관관계: 실제 DB FK 제약은 걸지 않는다. 연관은 상대 엔티티의 **id 값(Long)** 으로만 보유하고, 해당 컬럼에는 **인덱스만** 부여한다. (`@ManyToOne` / `@JoinColumn`(FK) 사용 금지)
- Redis: Master/Replica 구조 (Master: 쓰기, Replica: 읽기)

## HTTP Client

- API 테스트 파일: `http/` 디렉토리
- 환경 설정: `http/http-client.env.json` (local: `http://localhost:8080`)
