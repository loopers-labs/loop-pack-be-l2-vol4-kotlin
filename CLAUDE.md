# CLAUDE Working Guide

> 이 파일은 `AGENTS.md`를 Claude가 빠르게 파싱하도록 재작성한 본입니다.
> 충돌 시 `AGENTS.md`가 정답입니다 (이 파일이 누락/오역일 수 있음).
> 섹션 순서가 우선순위 — 위 섹션이 아래 섹션을 이깁니다.

## 0. Triggers — 이 상황이면 이 섹션부터

| 상황 | 섹션 |
|---|---|
| 비즈니스/애플리케이션 실패를 던지려 할 때 | §2 Error Handling |
| 새 도메인 에러 코드 정의 | §2 Error Handling |
| 컨트롤러/API 슬라이스 구현 시작 | §12 Account Notes — Implementation Direction |
| 컨트롤러 테스트 작성 | §12 Account Notes — Controller Test Strategy |
| 리포지토리/JPA 테스트 작성 | §12 Account Notes — Repository Test Strategy |
| 응답 DTO/래핑 결정 | §12 Account Notes — API Response Wrapping |
| 새 레이어/Facade/추상화 도입 충동 | §3 Design Principles, §12 |
| 버전/라이브러리 사용법 확인 | §10 Versions |
| 커밋/PR | §9 Git / PR |

---

## 1. Hard Rules

### MUST

- Kotlin + Java 21
- 패키지는 `com.loopers` 아래에 둔다
- `.editorconfig` 준수: IntelliJ Kotlin style, 130자 줄 제한, trailing commas, no wildcard imports
- 비즈니스/애플리케이션 실패는 §2의 `CoreException` 서브클래스 패턴으로만 던진다
- 도메인 에러 코드는 owning 도메인 모듈의 enum으로 정의하고 `com.loopers.support.error.ErrorCode`를 구현한다
- 코딩 전에 `gradle.properties`, `build.gradle.kts`로 버전을 확인하고, version-sensitive 동작은 공식 vendor docs로 검증한다
- 아키텍처 경계(`interfaces` / `application` / `domain` / `infrastructure`)를 보존한다 — 편의를 위해 도메인 규칙을 다른 레이어로 옮기지 않는다

### MUST NOT

- 시크릿 커밋 (`http/http-client.env.json`은 비민감하게 유지)
- ad hoc `RuntimeException`
- 앱 로컬 `CoreException` 재정의
- `ErrorType` 기반 모델 도입
- 새 `ErrorCode` 인터페이스 도입 / 공통 에러 코드 추상화 중복
- 단일 구현체를 위한 인터페이스
- 투기적 레이어/추상화/확장 포인트

---

## 2. Error Handling Pattern

**흐름:**
`CoreException` 서브클래스 throw → `ApiControllerAdvice`가 subclass별 HTTP 상태 매핑 → `ApiResponse.fail`이 `exception.errorCode.code` + `exception.message` 직렬화.

**HTTP-semantic wrapper (사용 가능한 것):**
- `BadRequestException`
- `UnauthorizedException`
- `ForbiddenException`
- `NotFoundException`
- `ConflictException`
- `InternalServerException`

**핵심 파일 위치:**
- 예외 베이스: `supports/error/src/main/kotlin/com/loopers/support/error/CoreException.kt`
- 매핑 어드바이스: `supports/web/src/main/kotlin/com/loopers/interfaces/api/ApiControllerAdvice.kt`
- 도메인 에러 코드 레퍼런스: `modules/account-domain/src/main/kotlin/com/loopers/account/domain/error/AccountErrorCode.kt`

**도메인 에러 코드 정의 패턴:**

```kotlin
enum class XxxErrorCode(override val message: String) : ErrorCode {
    SOME_FAILURE("사람이 읽는 메시지"),
    ;
    override val code: String = "XXX:$name"   // 도메인 prefix + enum 이름
}
```

**사용 패턴:**

```kotlin
throw ConflictException(AccountErrorCode.DUPLICATE_EMAIL)
```

**프레임워크가 자기 예외 타입을 요구할 때 (예: Spring Security):**
`CoreException`을 감싼다. 참고: `AccountAuthenticationException(coreException: CoreException)`.

**금지 예시:**
- ❌ `throw RuntimeException("이메일 중복")`
- ❌ 앱 모듈마다 `CoreException` 새로 정의
- ❌ `ErrorType` enum 도입
- ❌ 새로운 공통 `ErrorCode` 인터페이스 만들기

---

## 3. Design Principles

우선순위: **YAGNI → SOLID → Patterns**

- **YAGNI**: 지금 필요한 것만 빌드. 투기적 레이어/추상화 금지. 확장이 필요하다는 명확한 근거가 있으면 트레이드오프를 설명한 뒤 도입.
- **SOLID**: 모든 변경에서 고려. 단일 구현체용 인터페이스는 만들지 않음.
- **패턴**: 현재 코드를 단순화하거나 책임을 명확히 하거나 진짜 중복을 줄일 때만. 도입 전 benefit/cost 먼저 제시.
- **정당화**: 프로덕션 코드는 활성 테스트나 구체적 use case로 뒷받침되어야 한다.

---

## 4. Code Organization

**도메인 에러 코드 enum** → owning 도메인 모듈에 둔다 (application/interface 레이어 금지).

**한 owner의 cohesive 타입** → 같은 `.kt` 파일에 동거시킨다.
- 예시 1: `modules/account-application/src/main/kotlin/com/loopers/account/application/AccountService.kt`
  → `AccountService` 아래에 `AccountCreateCommand`, `AccountAuthenticateCommand`, account info DTO들이 함께 있음
- 예시 2: `modules/account-security/src/main/kotlin/com/loopers/account/security/AccountHeaderAuthenticationFilter.kt`
  → 필터 옆에 header/attribute 객체와 `AccountPrincipal`이 함께 있음

**별도 파일로 분리해야 할 때:**
- 여러 owner가 재사용
- 안정적 도메인/인프라 개념 (예: `AccountErrorCode.kt`, `domain/vo/*`, Redis configuration properties)

---

## 5. Project Structure

```
apps/
  commerce-api/         # active
  commerce-streamer/    # active
  commerce-batch/       # active
modules/                # 공유 인프라
supports/               # add-ons (error, web 등)
http/                   # HTTP 예시 (비민감 env.json)
docker/                 # local infra
```

Gradle 표준 경로: `src/main/kotlin`, `src/main/resources`, `src/test/kotlin`, test fixtures.

---

## 6. Testing

**스택:**
- JUnit 5
- Spring profile: `test`
- Timezone: `Asia/Seoul`
- Spring Boot Test, MockK/Mockito, Instancio, Testcontainers

**네이밍:**
| 종류 | 파일명 |
|---|---|
| Unit | `*Test.kt` |
| Integration | `*IntegrationTest.kt` |
| E2E | `*E2ETest.kt` |

**focused 실행:**
```bash
./gradlew :apps:commerce-api:test --tests '*ExampleServiceIntegrationTest'
```

> 컨트롤러/리포지토리별 구체 전략은 §12 Account Notes 참고.

---

## 7. Development Workflow

**TDD** → `$kent-beck-tdd` 스킬 사용:
1. JUnit 테스트 리스트 초안 작성
2. 사용자 confirmation
3. 실패하는 테스트 1개 작성
4. 통과시키는 최소 코드 구현
5. green 후 refactor

버전 가정이 구현 선택에 영향을 주면 PR 또는 짧은 주석에 기록.

---

## 8. Command Reference

| 명령 | 용도 |
|---|---|
| `make init` | hooks 설치 (pre-commit이 `./gradlew ktlintCheck` 실행) |
| `./gradlew build` | 모듈 컴파일 + 테스트 |
| `./gradlew test` | 테스트만 |
| `./gradlew ktlintCheck` | Kotlin 포맷 체크 |
| `./gradlew ktlintFormat` | Kotlin 포맷 적용 |
| `./gradlew :apps:commerce-api:bootRun --args='--spring.profiles.active=local'` | API 기동 |
| `docker-compose -f ./docker/infra-compose.yml up` | 의존성 기동 |

---

## 9. Git / PR

스킬: `$loopers-pr-workflow`

- 작업 베이스: 사용자 fork (`origin`)
- 최종 리뷰 PR target: `loopers-labs/loop-pack-be-l2-vol4-kotlin`, base `shoeone96`
- 커밋: Conventional Commit 스타일 prefix (예: `chore: PR 템플릿 통일 및 개선`), 짧고 scoped
- PR: `.github/pull_request_template.md` 따르기, 관련 시 테스트 결과 포함

---

## 10. Versions

코딩 전 체크리스트:
1. `gradle.properties`, `build.gradle.kts`에서 Java/Kotlin/Spring/Gradle 플러그인/라이브러리 버전 확인
2. version-sensitive 동작은 공식 vendor docs 우선
3. 자료 우선순위: **공식 docs > 메이저 테크 회사 엔지니어링 레퍼런스 > 개인 블로그**

---

## 11. Configuration & Security Files

런타임 config, 로컬 인프라, HTTP client 예시는 비즈니스 로직 변경과 분리한다.
태스크가 명시적으로 요구하지 않는 한 함께 수정하지 않는다.

---

## 12. Account 작업 노트 (Project-Specific)

### Account API Implementation Direction

- 현재 요구사항부터 바깥쪽으로 구현. 컨트롤러/API 계약을 먼저 만들고, 필요해진 다음 의존성만 추가한다.
- 명시적 요청이 없는 한 `V1` 네이밍이나 `/api/v1` 라우트를 도입하지 않는다. `AccountController`, `/accounts` 같이 단순한 이름을 쓴다.
- 요청 DTO는 단일 컨트롤러에서만 쓰이면 컨트롤러 파일에 함께 둔다. 재사용/크기 때문에 필요할 때만 별도 파일로 분리.
- 테스트/요구사항이 필요로 하기 전에 `Facade`, security, 추가 어댑터 레이어를 만들지 않는다.
- 실제 모듈 경계를 넘을 때만 `Command`를 사용한다. `AccountCreateCommand`가 API → application 입력 모델로 합의됨.
- 레이어가 필요해지면 focused test + 명확한 책임 경계와 함께 도입한다.

### Controller Test Strategy

| 도구 | 사용 시점 |
|---|---|
| `@WebMvcTest` | 컨트롤러 매핑/JSON 바인딩/요청·응답 shape만 필요할 때 (기본 선택) |
| `@SpringBootTest` + `@AutoConfigureMockMvc` | 전체 Spring context + `ControllerAdvice` + `ResponseBodyAdvice` + security가 실제 포트 없이 참여해야 할 때 |
| `@SpringBootTest(webEnvironment = RANDOM_PORT)` | 임베디드 서버로 진짜 HTTP/E2E 검증이 필요할 때만 |

현재 Account API thin slice에서는 진짜 HTTP 동작이 필요하지 않으면 `RANDOM_PORT`를 피한다.

### Repository Test Strategy

- 서비스 레이어 테스트는 도메인 리포지토리 port와 `PasswordEncryptor`를 mock 처리한다.
- 리포지토리 동작 검증은 기본적으로 `@DataJpaTest` + 임베디드 DB.
- MySQL-specific 동작을 검증해야 하는 경우가 아니면 MySQL/Testcontainers 설정을 import하지 않는다.
- `account-api`는 `src/test/resources/application-test.yaml`의 H2 임베디드 testdatasource를 사용한다.
- 테스트가 MySQL Testcontainers를 명시적으로 요구하지 않으면 `modules:jpa` test fixtures는 account-api 테스트 클래스패스에서 제외한다.
- account 영속성 동작은 `modules:account-persistence`에서 Spring Data JPA 리포지토리와 어댑터 와이어링으로 테스트한다.
- account 패키지 선호 때문에 `modules:jpa` `JpaConfig` 같은 베이스 템플릿 파일을 수정하지 않는다. account JPA 구현은 `modules:account-persistence`에 두고 account 전용 영속성 config로 연결한다.

### Module Boundaries

| 모듈 | 소유 |
|---|---|
| `account-domain` | entities, VO, validators, repository ports, `PasswordEncryptor` |
| `account-application` | use case, command, 트랜잭션 경계 |
| `account-persistence` | Spring Data JPA 리포지토리 + port 어댑터 |
| `account-security` | Spring Security crypto 어댑터 |
| `supports:error` | error code / exception (Spring MVC 상태 매핑 없음) |
| `supports:web` | 예외 → HTTP 응답 매핑, 성공 응답 래핑 |

### API Response Wrapping

- 컨트롤러에서 `ApiResponse`를 직접 반환하지 않는다.
- 컨트롤러는 일반 응답 body 또는 no body를 반환 → `ResponseBodyAdvice`가 성공 응답을 래핑.
- `ApiResponse`는 공통 web 인프라(예외 처리, 응답 advice, filter/security failure writer)에서만 사용.
- payload가 필요하면 도메인 전용 response DTO를 반환하고 advice가 래핑하게 한다.
- 아직 payload가 없으면 구조 충족 목적의 placeholder response DTO를 만들지 말고 body 없는 메서드로 둔다.
- 공유 Jackson `NON_NULL` 정책 준수: nullable 응답 필드(예: `data`)는 null일 때 생략 가능.

### TDD Flow Reminder

1. failing test 먼저 작성
2. 통과시키는 최소 코드 추가
3. green 후에만 refactor
4. 테스트 리스트는 MECE + 경계 중심으로 유지. 지금 필요하지 않은 동작에 대한 투기적 테스트는 추가하지 않는다.
