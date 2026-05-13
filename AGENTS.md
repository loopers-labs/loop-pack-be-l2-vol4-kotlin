# AGENTS.md

이 파일은 Codex가 이 저장소에서 작업할 때 우선 참고해야 하는 프로젝트 지침이다. 프로젝트 구조, 기술 스택, 개발 Workflow, 금지/권장 사항을 함께 정의한다.

## 프로젝트 개요

- 프로젝트명: `loopers-kotlin-spring-template`
- 그룹: `com.loopers`
- 언어/런타임: Kotlin + Java 21 toolchain
- 빌드 도구: Gradle Kotlin DSL
- 애플리케이션 유형: Spring Boot 기반 멀티 모듈 프로젝트
- 기본 테스트 설정:
    - JUnit Platform 사용
    - `spring.profiles.active=test`
    - `user.timezone=Asia/Seoul`
    - `maxParallelForks=1`

## 주요 기술 스택 및 버전

- Kotlin: `2.0.20`
- Spring Boot: `3.4.4`
- Spring Dependency Management: `1.1.7`
- Spring Cloud Dependencies: `2024.0.1`
- Java: `21`
- Ktlint Gradle Plugin: `12.1.2`
- Ktlint: `1.0.1`
- SpringDoc OpenAPI: `2.7.0`
- SpringMockK: `4.0.2`
- Mockito: `5.14.0`
- Mockito Kotlin: `5.4.0`
- Instancio JUnit: `5.0.2`
- Slack Logback Appender: `1.6.1`

## 모듈 구조

### `apps`

실행 가능한 Spring Boot 애플리케이션 모듈이다. `BootJar`가 활성화되고 일반 `Jar`는 비활성화된다.

- `apps:commerce-api`
    - Web API 애플리케이션
    - 의존 모듈: `modules:jpa`, `modules:redis`, `supports:jackson`, `supports:logging`, `supports:monitoring`
    - 주요 의존성: Spring Web, Actuator, SpringDoc OpenAPI, QueryDSL
    - 패키지 흐름은 기존 예시 기준으로 `interfaces` -> `application` -> `domain` -> `infrastructure` 계층을 따른다.
- `apps:commerce-batch`
    - Spring Batch 애플리케이션
    - 의존 모듈: `modules:jpa`, `modules:redis`, `supports:jackson`, `supports:logging`, `supports:monitoring`
    - 주요 의존성: Spring Batch, Spring Batch Test, QueryDSL
- `apps:commerce-streamer`
    - Kafka consumer/stream 처리 애플리케이션
    - 의존 모듈: `modules:jpa`, `modules:redis`, `modules:kafka`, `supports:jackson`, `supports:logging`,
      `supports:monitoring`
    - 주요 의존성: Spring Web, Actuator, Spring Kafka, QueryDSL

### `modules`

특정 애플리케이션에 종속되지 않는 재사용 가능한 인프라/설정 모듈이다. 공통 설정과 외부 시스템 연동 코드는 이 영역에 둔다.

- `modules:jpa`
    - Spring Data JPA, QueryDSL, MySQL connector
    - Testcontainers MySQL 기반 test fixtures 제공
- `modules:redis`
    - Spring Data Redis
    - Redis Testcontainers 기반 test fixtures 제공
- `modules:kafka`
    - Spring Kafka
    - Spring Kafka Test 및 Kafka Testcontainers 기반 test fixtures 제공

### `supports`

관측성, 로깅, 직렬화처럼 애플리케이션을 보조하는 add-on 모듈이다.

- `supports:jackson`
    - Jackson Kotlin module, JSR-310 날짜/시간 직렬화 설정
- `supports:logging`
    - Actuator, Prometheus, Micrometer tracing Brave bridge, Slack appender, Logback 설정
- `supports:monitoring`
    - Actuator, Prometheus registry 설정

## 개발 Workflow - 증강 코딩

- 방향성 및 주요 의사 결정은 개발자에게 제안하고, 승인된 방향을 기준으로 구현한다.
- 반복 작업, 요청 범위 밖 기능 구현, 테스트 삭제 또는 의미 약화가 필요한 경우에는 먼저 개발자에게 보고한다.
- AI는 설계 선택지를 제안할 수 있지만, 핵심 정책/도메인 방향/외부 계약 변경은 임의로 확정하지 않는다.
- 기존 코드 패턴과 계층 구조를 먼저 분석한 뒤, 가장 작은 변경으로 요구사항을 만족시킨다.

## 개발 Workflow - TDD

기능 추가 또는 버그 수정은 가능한 한 Red -> Green -> Refactor 순서로 진행한다.

### Red Phase

- 요구사항을 검증하는 실패 테스트를 먼저 작성한다.
- 테스트는 3A 원칙을 따른다.
    - Arrange: 테스트 데이터와 의존성 준비
    - Act: 검증 대상 동작 실행
    - Assert: 결과와 부작용 검증
- 단위 테스트만으로 부족한 경우 Spring Boot 통합 테스트 또는 Testcontainers 기반 테스트를 사용한다.

### Green Phase

- Red Phase의 테스트를 통과하는 최소 구현을 작성한다.
- 실제 동작하지 않는 임시 구현이나 불필요한 mock 데이터 기반 구현을 피한다.
- 오버엔지니어링하지 않고 현재 요구사항에 필요한 범위만 구현한다.

### Refactor Phase

- 중복 제거, 책임 분리, 이름 개선, 성능 개선을 수행한다.
- 불필요한 private 함수, unused import, 죽은 코드, `println`을 제거한다.
- 리팩터링 후 관련 테스트가 모두 통과해야 한다.

## 코드 작성 규칙

- Kotlin null-safety를 지킨다. nullable 타입은 명시적으로 다루고, 무분별한 `!!` 사용을 피한다.
- Java 코드를 작성해야 할 경우 nullable 결과는 가능하면 `Optional` 등 명시적 타입으로 표현한다.
- thread-safety가 필요한 영역에서는 공유 mutable state를 피하고, 동시성 경계를 명확히 한다.
- 도메인 로직은 테스트 가능한 구조로 설계한다.
- 컨트롤러/외부 인터페이스 DTO와 도메인 모델의 책임을 섞지 않는다.
- 외부 시스템 연동은 `modules` 또는 `infrastructure` 계층에 두고, 도메인 계층이 구체 기술에 직접 의존하지 않도록 한다.
- 신규 API가 완성되면 가능한 경우 `http/**/*.http`에 실행 가능한 요청 예시를 추가한다.

## Never Do

- 실제로 동작하지 않는 코드나 하드코딩된 가짜 성공 구현을 남기지 않는다.
- 테스트를 통과시키기 위해 의미 없는 mock 데이터나 검증 없는 stub만 추가하지 않는다.
- null-safety를 깨는 코드를 작성하지 않는다.
- `println` 또는 임시 디버깅 로그를 남기지 않는다.
- 테스트 삭제, assertion 약화, `@Disabled` 추가는 개발자 승인 없이 하지 않는다.
- 기존 public API, DB schema, 메시지 계약, configuration key를 임의로 변경하지 않는다.

## Recommendation

- 실제 API 흐름을 검증할 수 있는 E2E 또는 통합 테스트를 우선 고려한다.
- MySQL, Redis, Kafka 연동 검증에는 기존 Testcontainers/test fixtures 구조를 활용한다.
- 재사용 가능한 객체와 설정은 `modules` 또는 `supports`에 배치한다.
- 성능 최적화가 필요한 경우 구현 전에 병목 가설과 대안을 함께 제안한다.
- API 변경 시 문서, `.http` 요청 예시, 테스트를 함께 갱신한다.

## 우선순위

1. 실제 동작하는 해결책만 고려한다.
2. null-safety와 thread-safety를 고려한다.
3. 테스트 가능한 구조로 설계한다.
4. 기존 코드 패턴과 모듈 경계를 분석한 뒤 일관성을 유지한다.
5. 불필요한 추상화보다 명확하고 작은 변경을 우선한다.

## 자주 사용하는 명령

```shell
./gradlew test
./gradlew ktlintCheck
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-batch:test
./gradlew :apps:commerce-streamer:test
```

Windows PowerShell 환경에서는 필요하면 다음처럼 Gradle wrapper를 실행한다.

```powershell
.\gradlew.bat test
.\gradlew.bat ktlintCheck
```

## 검증 기준

- 코드 변경 후 최소한 변경 범위에 해당하는 모듈 테스트를 실행한다.
- 공통 모듈(`modules`, `supports`) 변경 시 영향을 받는 앱 테스트도 함께 고려한다.
- 스타일 변경 또는 Kotlin 코드 추가 후 `ktlintCheck`를 고려한다.
- 테스트를 실행하지 못한 경우, 이유와 남은 리스크를 개발자에게 명확히 보고한다.
