# 인증 — 아키텍처

범위: 회원가입, 내 정보 조회, 비밀번호 변경 엔드포인트 및 모든 보호된 엔드포인트가 공유하는 헤더 기반 인증 메커니즘.

## 목표 및 비목표

- **목표**
  - 기능 명세에 따라 회원가입, 내 정보 조회, 비밀번호 변경을 구현한다.
  - 헤더 기반의 무상태(stateless) 인증 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`).
  - 비즈니스 로직을 Spring 프레임워크로부터 독립적으로 유지 — 도메인 타입은 Spring을 import해서는 안 된다.
  - 보호된 엔드포인트 집합을 grep으로 찾기 쉽고, 리팩터링에 의해 변조되기 어렵게 만든다.
- **비목표**
  - Spring Security를 사용하지 않는다. 팀은 그로 인한 리스크(매 요청마다 비밀번호 전송, 세션 없음, 요청 제한 없음, CSRF/CORS 의견 없음)를 수용한다.
  - 이 기능에는 facade 레이어를 두지 않는다. 정당화할 만한 모듈 간 오케스트레이션이 없으므로 컨트롤러가 도메인 서비스와 직접 통신한다.
  - 로그인/로그아웃 엔드포인트 없음, 토큰 발급 없음, 리프레시 플로우 없음. 인증은 "매 요청마다 자격 증명 검증"이다.

## 레이어 구조

```
interfaces/api/user/
  ├── UserV1Controller.kt
  ├── UserV1ApiSpec.kt
  └── UserV1Dto.kt

domain/user/
  ├── User.kt                  JPA 엔티티, BaseEntity 상속
  │                            필드: loginId: String, encryptedPassword: String,
  │                                  name: String, birthdate: LocalDate, email: String
  ├── UserService.kt           @Component, @Transactional
  ├── UserRepository.kt        포트 (인터페이스)
  ├── PasswordEncoder.kt       포트 (인터페이스)
  └── RawPassword.kt           값 클래스 — 유일하게 살아남은 래퍼
                               (생성 시 정규식 검증; toString() 마스킹 처리)

infrastructure/user/
  ├── UserRepositoryImpl.kt    domain.user.UserRepository 구현
  ├── UserJpaRepository.kt     Spring Data
  └── BCryptPasswordEncoder.kt domain.user.PasswordEncoder 구현
                               (at.favre.lib:bcrypt 사용 — Spring Security 없음)

support/auth/
  ├── CurrentUser.kt                  파라미터 어노테이션
  ├── LoginRequired.kt                메서드/클래스 어노테이션
  ├── AuthenticationInterceptor.kt    HandlerInterceptor — 인증 및 강제 수행
  ├── CurrentUserArgumentResolver.kt  HandlerMethodArgumentResolver — 요청 스코프에서 읽음
  └── AuthWebMvcConfigurer.kt         인터셉터 + 리졸버 등록
```

### `support/`에 대한 컨벤션 메모

이전에는 `support/`가 Spring과 무관한 코드(`support/error`)만을 담고 있었다. 이번 변경으로 "범기능적 공통 패키지; Spring 결합 허용"이 된다. 그룹화 규칙은 응집도(어노테이션과 그것이 구동하는 Spring 컴포넌트는 함께 이동해야 함)이지, Spring 결합 여부가 아니다. `support/error`의 순수성을 보고 `support/`가 Spring과 무관한 영역이라고 추론하지 말 것.

### facade를 두지 않는 이유

프로젝트 CLAUDE.md에 따르면 표준 흐름은 `Controller → Facade → Service → Repository`이다. 이 기능에서 facade는 단일 라인 패스스루가 될 것이다 — 함께 오케스트레이션할 두 번째 도메인이 없다. 컨트롤러는 `UserService`에 직접 의존한다. 향후 기능(예: 포인트, 주문)이 사용자 작업과 합성되어야 한다면, 그때 `application/user/UserFacade`를 도입한다.

## 인증 파이프라인

```
HTTP 요청
  │
  ▼
AuthenticationInterceptor.preHandle(req, resp, handler)
  ├─ handler가 HandlerMethod가 아닐 경우 → return true (정적 리소스, 에러 디스패치)
  ├─ handler 메서드(및 선언 클래스)에서 @LoginRequired 확인
  │     ├─ 없음 → return true (공개 엔드포인트; 헤더 무시)
  │     └─ 있음 → 계속 진행
  ├─ X-Loopers-LoginId / X-Loopers-LoginPw 읽기
  │     ├─ 둘 중 하나라도 없거나 공백 → CoreException(UNAUTHORIZED) 던짐
  │     └─ 둘 다 존재 → 계속 진행
  ├─ userService.authenticate(loginId, rawPw)
  │     ├─ 사용자 없음 / 해시 불일치 → CoreException(UNAUTHORIZED) 던짐
  │     └─ 성공 → req.setAttribute(CURRENT_USER_KEY, user); return true
  │
  ▼
CurrentUserArgumentResolver.resolveArgument
  ├─ req.getAttribute(CURRENT_USER_KEY) 읽기
  ├─ 없음 → IllegalStateException 던짐
  │     ("@LoginRequired 없이 @CurrentUser 사용" — 프로그래밍 오류이지 HTTP 401이 아님)
  └─ 있음 → User 반환
  │
  ▼
컨트롤러 메서드 본문
```

### 확정된 규칙

- **`@LoginRequired`는 모든 보호된 엔드포인트에 필수다.** 예외 없음. 보호된 집합은 `grep -r @LoginRequired apps/commerce-api/src/main`이다.
- **`@CurrentUser`는 단순 리더(reader)다.** 절대로 인증을 강제하지 않는다. 요청 스코프에 사용자가 없다면 = 프로그래머 오류(`@CurrentUser`가 `@LoginRequired` 없이 사용됨)이지, HTTP 401이 아니다.
- **공개 엔드포인트는 헤더를 완전히 무시한다.** 회원가입은 헤더가 존재하더라도 기회주의적 인증을 시도하지 않는다.
- **인증 실패는 `CoreException(ErrorType.UNAUTHORIZED, …)`를 던진다.** `ApiControllerAdvice`가 이미 `CoreException`을 표준 envelope로 매핑하므로, `support/auth/`에서 별도의 변환은 필요하지 않다.

## 도메인 모델

### `RawPassword` (유일하게 살아남은 래퍼)

```kotlin
@JvmInline
value class RawPassword(val value: String) {
    init {
        require(value.matches(PATTERN)) { ... }   // CoreException(BAD_REQUEST) 던짐
    }
    override fun toString(): String = "***"        // 평문 비밀번호를 절대 로깅하지 않음
    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9!@#$%^&*()\\-_=+\\[\\]{};:'\",.<>/?\\\\|`~]{8,16}$")
    }
}
```

이것이 살아남은 두 가지 이유:
- `PasswordEncoder.encode(raw: RawPassword): String`은 방향성을 자체적으로 문서화한다. 일반 문자열은 혼동될 수 있지만, 이 시그니처는 그럴 수 없다.
- 마스킹된 `toString()`은 실제 발 디딜 위험(로그 라인에 비밀번호가 우연히 출력되는 사고)을 제거한다.

### `User` 엔티티 — `init`에서 검증

`BaseEntity` 상속 (`id`, `createdAt`, `updatedAt`, `deletedAt`). `data class` 아님 (`.coderabbit.yaml`에 따라). 필드 타입은 래퍼가 아닌 평범한 Kotlin/JDK 타입이다:

```kotlin
@Entity
class User(
    val loginId: String,
    var encryptedPassword: String,
    val name: String,
    val birthdate: LocalDate,
    val email: String,
) : BaseEntity() {
    init {
        require(loginId.matches(LOGIN_ID_PATTERN)) { ... }
        require(name.isNotBlank()) { ... }
        require(email.matches(EMAIL_PATTERN)) { ... }
        require(birthdate.isBefore(LocalDate.now())) { ... }
        // encryptedPassword는 불투명 — 인코더를 신뢰
    }

    fun changePassword(newEncrypted: String) { this.encryptedPassword = newEncrypted }
}
```

- 필드 단위 형식 규칙은 `init`에 위치한다 (`loginId` 정규식, `name` 비공백, `email` 정규식, `birthdate` 과거).
- 필드 간 규칙(비밀번호 내 생년월일)과 유일성(`loginId`)은 `UserService`에 위치한다 — 단일 객체 생성만으로는 강제할 수 없기 때문이다.
- `User`는 `encryptedPassword`의 내용을 검증하지 않는다; 인코더를 신뢰한다.

`init` 블록의 실패는 `CoreException(ErrorType.BAD_REQUEST, …)`를 던져 기존 `ApiControllerAdvice`가 표준 envelope를 생성하도록 해야 한다.

### 포트

```kotlin
interface UserRepository {
    fun findByLoginId(loginId: String): User?
    fun existsByLoginId(loginId: String): Boolean
    fun save(user: User): User
}

interface PasswordEncoder {
    fun encode(raw: RawPassword): String
    fun matches(raw: RawPassword, encoded: String): Boolean
}
```

`infrastructure/user/BCryptPasswordEncoder`는 `at.favre.lib:bcrypt`를 사용한다 — "Spring Security 없음" 결정과의 경계를 명확히 유지한다.

## 비밀번호 내 생년월일 규칙

회원가입과 비밀번호 변경 시 모두 `UserService`에서 강제한다.

```
forbiddenSubstrings = [
    birthdate.format("yyyyMMdd"),
    birthdate.format("yyMMdd"),
    birthdate.format("MMdd"),
]

digitGroups = Regex("\\d+").findAll(rawPassword).map { it.value }

만약 어떤 digitGroup이라도 forbiddenSubstring을 포함한다면 → BAD_REQUEST 던짐
```

이 규칙은 의도적으로 엄격하다: 생일 `1990-01-01` (`MMdd = "0101"`)의 경우, `1234560101`과 같은 비밀번호 숫자열은 비록 `"0101"`이 그 위치에서 문자 그대로 날짜가 아니더라도 거부된다. 이는 단순하고 감사 가능한 규칙을 얻는 대가로 수용되는 위양성(false positive) 리스크다.

## `changePassword` 시맨틱

요청 본문은 `{ oldPassword, newPassword }`를 담는다. 인터셉터가 이미 헤더 비밀번호를 검증했음에도, 서비스는 **본문의 `oldPassword`를 저장된 해시와 재검증한다**. 그 이유:

- Sudo 스타일의 재인증: 명시적인 "현재 비밀번호를 입력하세요" 제스처는 민감한 행동에 대한 의미 있는 사용자 의도이지, 보여주기식이 아니다.
- 서비스 계약이 깔끔하게 유지된다 — HTTP 헤더에 대해 알 필요가 없다.
- 본문의 `oldPassword`와 헤더 비밀번호가 일치하지 않으면, 서비스 레이어에서 `BAD_REQUEST`로 거부한다 (둘 중 어느 쪽도 암묵적으로 신뢰하지 않는다).

비밀번호 변경 시 서비스 레이어에서의 추가 검증:
- `newPassword`는 `RawPassword` 값 객체 규칙(형식, 길이)을 만족해야 한다.
- `newPassword`는 비밀번호 내 생년월일 규칙을 위반해서는 안 된다.
- `newPassword`는 현재 저장된 비밀번호와 일치해서는 안 된다 (`!encoder.matches(newPassword, user.encryptedPassword)`).

## HTTP 계약 (스케치)

| 메서드 | 경로 | 인증 | 본문 | 응답 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/users` | 공개 | `{ loginId, password, name, birthdate, email }` | `MyInfoResponse` |
| GET | `/api/v1/users/me` | `@LoginRequired` | — | `MyInfoResponse` |
| PATCH | `/api/v1/users/me/password` | `@LoginRequired` | `{ oldPassword, newPassword }` | 빈 성공 응답 |

`MyInfoResponse = { loginId, name (마스킹됨), birthdate, email }`. 이름 마스킹(마지막 글자를 `*`로 치환)은 표현 계층(presentation)의 관심사로, DTO 매퍼(`MyInfoResponse.from(user)`) 내부에서 수행한다. 도메인은 전체 이름을 반환한다; 마스킹은 렌더링하는 측의 책임이다.

모든 응답은 `ApiResponse.success(...)`를 통해 전달된다. 모든 에러 경로는 `CoreException(ErrorType, …)`을 던진다; `ApiControllerAdvice`가 변환을 처리한다.

## 컨트롤러 형태 (예시)

```kotlin
@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userService: UserService,
) : UserV1ApiSpec {

    @PostMapping
    override fun signUp(
        @RequestBody @Valid request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.MyInfoResponse> =
        userService.register(request.toCommand())
            .let { UserV1Dto.MyInfoResponse.from(it) }
            .let { ApiResponse.success(it) }

    @LoginRequired
    @GetMapping("/me")
    override fun getMyInfo(
        @CurrentUser user: User,
    ): ApiResponse<UserV1Dto.MyInfoResponse> =
        ApiResponse.success(UserV1Dto.MyInfoResponse.from(user))

    @LoginRequired
    @PatchMapping("/me/password")
    override fun changePassword(
        @CurrentUser user: User,
        @RequestBody @Valid request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Unit> {
        userService.changePassword(user.id, request.oldPassword, request.newPassword)
        return ApiResponse.success(Unit)
    }
}
```

## 수용한 트레이드오프 (재논의하지 않도록 적어둔다)

- **요청마다 bcrypt 검증**은 비용이 크다(보호된 호출당 ~50–200ms). 세션 없는 무상태 헤더 인증의 비용으로 수용한다. loginId → (hash, userId)를 짧은 TTL의 Redis 캐시로 두는 것은 향후 옵션이다; 지금은 하지 않는다.
- **Spring Security 미사용.** `spring-security-crypto`만 의존성으로 끌어오는 것(bcrypt를 위해서만)이 가장 작은 양보겠지만, "Spring Security 없음"의 경계를 모호하지 않게 유지하기 위해 `at.favre.lib:bcrypt`를 선택했다.
- **비밀번호 내 생년월일 검증은 과하게 엄격하다.** 위양성(예: `1234560101`)은 한 줄짜리 감사 및 설명이 쉬운 규칙을 얻기 위한 대가로 수용한다.
- **facade 미도입.** 두 번째 도메인(포인트/주문 등)이 사용자 작업과 합성되어야 할 때 다시 도입한다.
- **`support/`는 더 이상 Spring과 무관한 영역이 아니다.** 이 패키지에서는 결합 축의 순수성보다 응집도가 우선한다.

## 명시적으로 기각된 결정들

- **`@LoginRequired`를 인터셉터와 분리된 AOP aspect로 두는 것.** 기각: 기능적 이점 없이 인증 결정이 두 곳(인터셉터의 헤더 읽기, aspect의 강제)으로 쪼개진다.
- **`@CurrentUser` 리졸버가 자체적으로 인증을 강제하는 것.** 기각: `@LoginRequired`가 필수이므로 인터셉터가 이미 (저장했거나 거부한) 상태가 보장된다. 중복 강제는 동일 조건에 대해 두 개의 에러 경로를 만든다.
- **DTO 레벨의 비즈니스 검증 (엔티티 정규식을 미러링한 `@field:Pattern`).** 기각: 같은 규칙에 대한 두 개의 진실 원천(source of truth)은 시간이 지나며 어긋난다. DTO는 형식 검증(`@NotBlank` / `@NotNull` / `@Email`)을 담당한다; `User` 엔티티의 `init` 블록이 비즈니스 형식을 소유한다; `UserService`가 필드 간 규칙을 소유한다.

- **모든 필드를 값 객체로 래핑하는 것 (`LoginId`, `Name`, `Email`, `Birthdate`, `EncryptedPassword`).** 기각: 회원가입 폼 하나에 여섯 개의 래퍼는 보상 없는 형식이다. CRUD 엔티티에서 `name`과 `email`을 혼동할 위험은 실재하지 않는다. `RawPassword`는 예외다 — 위 참조. `EncryptedPassword`도 폐기 (JPA 편의를 위해 `String`으로 유지).
- **`changePassword`에서 헤더 비밀번호를 "이전 비밀번호"로 신뢰하는 것.** 기각: 서비스 계약이 HTTP를 인지하게 되고, 의도적인 사용자 의도 제스처가 제거된다.

## 미결 항목 (아키텍처가 아닌 구현용)

- 값 객체에 대한 정확한 컬럼 매핑 전략 (`@Embedded` vs 스칼라 컬럼 + `@Convert`).
- Bean Validation 메시지 번들 셋업 (i18n은 범위 밖이지만 에러 메시지 문자열의 일관성은 필요).
- Instancio 기반 `User` 테스트 픽스처 — `domain/user/` 테스트 소스 옆에 빌더 헬퍼 정의.
