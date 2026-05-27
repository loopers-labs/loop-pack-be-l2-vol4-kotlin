# CLAUDE.md

## 프로젝트 개요

Loopers에서 제공하는 Spring Kotlin 기반 커머스 서비스 템플릿입니다.
Gradle 멀티 모듈 구조로 구성되어 있으며, HTTP API, Batch, Streamer 애플리케이션을 분리해 운영합니다.

## 기술 스택

- Kotlin `2.0.20`
- Java Toolchain `21`
- Gradle Wrapper `8.13`
- Spring Boot `3.4.4`
- Spring Dependency Management `1.1.7`
- Spring Cloud Dependencies `2024.0.1`
- ktlint Gradle Plugin `12.1.2`, ktlint `1.0.1`
- SpringDoc OpenAPI `2.7.0`
- JPA, QueryDSL, MySQL Connector
- Redis, Kafka 설정 모듈
- 테스트: JUnit 5, Spring Boot Test, SpringMockK, Mockito, Instancio, Testcontainers

## 모듈 구조

- `apps`
  - `commerce-api`: HTTP API 애플리케이션
  - `commerce-batch`: Spring Batch 애플리케이션
  - `commerce-streamer`: Kafka Consumer 애플리케이션
- `modules`
  - `jpa`: JPA, DataSource, QueryDSL, 테스트 픽스처 설정
  - `redis`: Redis 설정 및 테스트 픽스처
  - `kafka`: Kafka 설정
- `supports`
  - `jackson`: 공통 Jackson 설정
  - `logging`: Logback Appender 및 프로필별 로깅 설정
  - `monitoring`: Actuator, Prometheus, Grafana 설정

## 개발 규칙

### 진행 Workflow - 증강 코딩

- 개발 방향과 주요 의사결정의 최종 주도권은 개발자에게 있습니다.
- AI는 방향을 제안할 수 있지만, 승인된 결정과 `plan.md`의 다음 항목을 기준으로 작업합니다.
- 반복적인 동작, 범위 확장, 테스트 삭제 또는 약화가 필요해 보이면 즉시 멈추고 보고합니다.
- 구조 변경과 기능 변경은 가능하면 분리합니다.
- 분위기로 밀어붙이지 않고, 테스트와 동작으로 확인되는 구현만 남깁니다.

### 개발 Workflow - TDD

- Red -> Green -> Refactor 순서를 따릅니다.
- 하나의 작은 동작에 대해 실패하는 테스트를 먼저 작성합니다.
- 테스트는 3A 패턴을 따릅니다: Arrange, Act, Assert.
- Green 단계에서는 현재 실패한 테스트를 통과시키는 만큼만 구현합니다.
- Refactor 단계는 관련 테스트가 통과한 상태에서만 진행합니다.
- 요청되지 않았거나 테스트로 표현되지 않은 기능은 임의로 추가하지 않습니다.

## 설계 원칙

### 도메인 & 객체 설계 전략

- 도메인 객체는 비즈니스 규칙과 상태 변경 규칙을 스스로 캡슐화합니다.
- 도메인 규칙이 여러 서비스 또는 유스케이스에 반복되면 도메인 객체, 값 객체, 도메인 서비스로 이동할 가능성을 먼저 검토합니다.
- 애플리케이션 계층은 여러 도메인 객체와 Repository Port를 조립해 사용자의 유스케이스를 완성합니다.
- 애플리케이션 계층은 트랜잭션 경계, 흐름 제어, 도메인 객체 간 협력 조정을 담당하고, 핵심 비즈니스 판단을 직접 소유하지 않습니다.
- 도메인 객체는 외부 프레임워크, API DTO, 영속성 구현 세부사항에 의존하지 않습니다.
- 엔티티는 불변 조건을 깨지 않는 생성 메서드와 행위 메서드를 제공하고, 외부에서 임의로 상태를 변경할 수 없도록 합니다.
- 값 객체는 검증과 동등성 비교가 필요한 개념에 우선 적용합니다.
- 각 기능을 구현하기 전 책임 배치, 객체 협력 방식, 결합도에 대한 개발자의 의도를 확인하고 진행합니다.

### Usecase 중심 객체 협력 설계

- 하나의 사용자 기능은 하나의 명확한 Usecase로 표현합니다.
- Usecase는 입력 DTO를 받아 도메인 객체를 조회, 생성, 변경한 뒤 출력 DTO를 반환합니다.
- Usecase 입력 DTO와 출력 DTO는 API request, response DTO와 분리합니다.
- Usecase는 구체적인 JPA Repository, Redis Client, 외부 API Client가 아니라 도메인 계층의 Repository Port 인터페이스에 의존합니다.
- 단순 CRUD처럼 흐름이 작더라도 Controller에서 Repository를 직접 호출하지 않고 Usecase를 경유합니다.
- 여러 Usecase에서 공유되는 조회 모델은 중복을 제거하되, 도메인 규칙을 표현하지 않는 조회 편의 로직과 핵심 도메인 행위를 혼동하지 않습니다.
- 유스케이스 테스트는 객체 협력과 결과를 검증하고, 도메인 테스트는 비즈니스 규칙을 직접 검증합니다.

### 아키텍처, 패키지 구성 전략

- 본 프로젝트는 Clean Architecture 원칙을 따르며, 현재 코드베이스의 4개 레이어 패키지 구조를 유지합니다.
- 의존성 방향은 바깥 레이어에서 안쪽 레이어로만 향합니다: `interfaces -> application -> domain`.
- `infrastructure`는 `domain`에 정의된 Repository Port 인터페이스를 구현하며, 도메인 로직을 소유하지 않습니다.
- Repository Port는 기존 구조를 따라 `domain/{domain}` 패키지에 둡니다.
- DIP를 준수하기 위해 Repository, 외부 시스템 연동, 시간, ID 생성 등 변경 가능한 세부사항은 인터페이스 뒤에 둡니다.
- API request, response DTO와 application 계층의 command, query, result DTO는 분리합니다.
- 패키징은 레이어를 먼저 나누고, 하위에 도메인별 패키지를 둡니다.

```text
com.loopers
  interfaces/api/{domain}       # presentation layer: Controller, API DTO, API Spec
  application/{domain}          # application layer: Usecase, Command, Query, Result
  domain/{domain}               # domain layer: Entity, Value Object, Domain Service, Repository Port
  infrastructure/{domain}       # infrastructure layer: JPA Entity/Repository, Adapter, Repository 구현체
```

- 새로운 기능은 가능한 한 `application/{domain}/usecase` 단위로 진입점을 만들고, 기존 `Facade` 패턴과 함께 사용할 경우 책임이 겹치지 않도록 정리합니다.
- `interfaces` 계층은 인증 정보 추출, 요청 검증, DTO 변환, 응답 코드 매핑에 집중합니다.
- `application` 계층은 유스케이스별 트랜잭션과 도메인 객체 협력을 조정합니다.
- `domain` 계층은 비즈니스 규칙, 상태 전이, 도메인 예외, Repository Port를 포함합니다.
- `infrastructure` 계층은 JPA, Redis, Kafka, 외부 API 같은 기술 세부사항을 캡슐화합니다.

## 주의사항

### Never Do

- 실제 동작하지 않는 코드나 임시 Mock 데이터 기반 구현을 남기지 않습니다.
- Kotlin null-safety를 깨는 방식으로 작성하지 않습니다.
- `println`이나 임시 디버그 코드를 남기지 않습니다.
- 테스트를 임의로 삭제하거나 검증을 약화하지 않습니다.

### Recommendation

- 기존 패키지와 레이어링을 따릅니다: `domain -> application -> interfaces`, 영속성 구현은 `infrastructure`.
- 예상 가능한 비즈니스 실패는 `CoreException`과 적절한 `ErrorType`으로 표현합니다.
- API 구현이 완료되면 `.http` 파일에 실행 예시를 추가합니다.
- 복잡한 추상화보다 요구사항을 만족하는 단순한 설계를 우선합니다.

### Priority

1. 테스트로 증명되는 실제 동작
2. 명확한 도메인 규칙과 null-safety
3. 기존 프로젝트 패턴과의 일관성
4. 단순한 구현
5. 현재 요구사항에 필요한 경우에만 성능과 동시성 개선
