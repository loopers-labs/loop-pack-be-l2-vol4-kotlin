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

항상 `plan.md`의 지시를 따른다. 개발자가 `"go"`라고 말하면 `plan.md`에서 아직 표시되지 않은 다음 테스트를 찾아 구현하고, 그 테스트를 통과시키는 데 필요한 최소한의 코드만 작성한다.

이 프로젝트에서 TDD는 Kent Beck의 Red -> Green -> Refactor와 Tidy First 원칙을 정확히 따르는 것을 의미한다.

### 핵심 원칙

- 항상 TDD 사이클 `Red -> Green -> Refactor`를 따른다.
- 가장 단순한 실패 테스트부터 작성한다.
- 테스트를 통과시키는 데 필요한 최소한의 코드만 작성한다.
- 리팩터링은 테스트가 모두 통과한 뒤에만 수행한다.
- 구조 변경과 동작 변경을 분리하는 `Tidy First` 원칙을 따른다.
- 개발 내내 높은 코드 품질을 유지한다.

### TDD 진행 방식

- 작은 기능 증가분을 정의하는 실패 테스트부터 작성한다.
- 테스트 이름은 동작을 설명하도록 명확하게 작성한다.
    - 예시: `shouldSumTwoPositiveNumbers`
- 테스트 실패 원인은 분명하고 해석 가능해야 한다.
- 테스트를 통과시키는 데 필요한 만큼만 구현하고, 그 이상은 작성하지 않는다.
- 테스트가 통과한 뒤에만 리팩터링이 필요한지 검토한다.
- 새 기능은 이 사이클을 반복하며 점진적으로 확장한다.
- 버그를 수정할 때는 먼저 API 수준의 실패 테스트를 작성하고, 그 다음 문제를 최소 재현하는 가장 작은 테스트를 작성한 뒤 두 테스트를 모두 통과시킨다.

### Tidy First 원칙

- 모든 변경은 다음 두 종류로 분리한다.
    1. 구조적 변경: 동작을 바꾸지 않고 코드만 재배치하는 변경
    2. 동작 변경: 실제 기능을 추가하거나 수정하는 변경
- 구조적 변경과 동작 변경을 하나의 커밋에 섞지 않는다.
- 두 종류의 변경이 모두 필요하면 항상 구조적 변경을 먼저 수행한다.
- 구조적 변경 전후에는 테스트를 실행해 동작이 바뀌지 않았음을 검증한다.

### 커밋 원칙

- 커밋은 아래 `커밋 작성 가이드`를 따른다.
- TDD 작업에서도 커밋 단위, Red 커밋 허용 범위, 메시지 규약은 `커밋 작성 가이드`를 우선한다.

### 코드 품질 기준

- 중복은 집요하게 제거한다.
- 이름과 구조를 통해 의도를 명확하게 드러낸다.
- 의존성은 숨기지 않고 명시적으로 표현한다.
- 메서드는 작고 하나의 책임에 집중하게 유지한다.
- 상태와 부작용은 최소화한다.
- 현재 요구사항을 만족하는 가장 단순한 해법을 선택한다.

### 리팩터링 원칙

- 리팩터링은 반드시 테스트가 통과한 `Green` 상태에서만 수행한다.
- 검증된 리팩터링 패턴을 사용하고, 가능하면 그 이름을 의식하며 적용한다.
- 한 번에 하나의 리팩터링만 수행한다.
- 각 리팩터링 단계마다 테스트를 다시 실행한다.
- 중복 제거와 가독성 향상에 기여하는 리팩터링을 우선한다.

### 기본 작업 흐름

1. 기능의 작은 일부를 검증하는 단순한 실패 테스트를 작성한다.
2. 그 테스트를 통과시키는 최소 구현을 작성한다.
3. 테스트를 실행해 실제로 통과하는지 확인한다.
4. 필요한 구조적 변경이 있다면 `Tidy First` 원칙에 따라 하나씩 적용하고, 각 단계마다 테스트를 실행한다.
5. 구조적 변경은 동작 변경과 분리해 별도로 커밋한다.
6. 다음 작은 기능 증가분에 대한 테스트를 추가한다.
7. 기능이 완성될 때까지 이 과정을 반복한다.

항상 한 번에 테스트 하나만 작성하고, 그 테스트를 실행 가능하게 만든 다음, 구조를 개선한다. 매 사이클마다 장시간 실행되는 테스트를 제외한 전체 테스트를 반드시 실행한다.

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

## 커밋 작성 가이드

### 커밋 단위

- 커밋은 작업 완료 단위가 아니라 검증 가능한 가장 작은 변경 단위로 나눈다.
- 큰 커밋보다 작고 자주 하는 커밋을 선호한다.
- 테스트 추가, 구현 추가, 리팩터링, 문서 수정, fixture 정리, 테스트 환경 수정은 가능한 한 서로 다른 커밋으로 분리한다.
- 하나의 커밋은 하나의 테스트 의도 또는 하나의 구현 의도만 가져야 한다.
- TDD 작업에서는 테스트를 추가하는 `test:` 커밋과 그 테스트를 통과시키는 `feat:` 또는 `fix:` 커밋을 분리한다.
- 리팩터링은 동작 변경과 분리해 `refactor:` 커밋으로 남긴다.
- 이미 작성한 커밋이 너무 크거나 테스트와 구현이 섞였다고 판단되면, 개발자에게 보고하고 rebase/cherry-pick으로 커밋을 재분리한다.

### TDD 커밋 기준

- 다음 조건을 모두 만족할 때만 커밋한다.
    1. 변경이 하나의 논리적 작업 단위를 이룬다.
    2. 컴파일 오류, 타입 오류, import 오류, unresolved reference가 없다.
    3. 컴파일러 및 린터 경고가 모두 해소되어 있다.
    4. 커밋 메시지에 변경 성격과 작업 대상이 분명히 드러난다.
- `test:` 커밋이라도 테스트 컴파일을 위한 최소 production 시그니처 추가는 포함할 수 있다. 단, 테스트를 통과시키는 실제 동작 구현은 포함하지 않는다.
- Red 커밋은 컴파일 가능한 상태여야 하며, 실패 원인은 컴파일 오류가 아니라 테스트 검증 실패여야 한다.
- Red 커밋 전에 최소한 해당 테스트 task를 실행해 컴파일은 통과하고 테스트 검증 결과만 실패하는지 확인한다.
- Green, Refactor, Docs, Chore 성격의 커밋은 관련 테스트 또는 검증 명령이 통과한 상태에서만 남긴다.

### 커밋 메시지

커밋 메세지는 명확한 동사 + 작업 대상 구조로 작성합니다.

(예시)
- feat: 주문 생성 기능 구현
- refactor: 장바구니 엔티티 리팩토링
- test: 주문 생성 테스트 코드 추가
- docs 문서 수정
- style 코드 스타일 수정
- chore	기타 작업
