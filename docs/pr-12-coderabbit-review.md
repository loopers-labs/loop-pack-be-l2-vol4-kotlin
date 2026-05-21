# PR #12 CodeRabbit 리뷰 — 의사결정 워크북

> **PR**: [loopers-labs/loop-pack-be-l2-vol4-kotlin#12](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12)
> **브랜치**: `assignment/week-01-member-account` → `shoeone96`
> **수집 코멘트**: 인라인 16개 + walkthrough 1개
> **작성일**: 2026-05-17

---

## 0. 사용 가이드

이 문서는 CodeRabbit이 남긴 16개 코멘트를 **사용자가 항목별로 의사결정**하기 위한 워크북이다.

각 항목은 다음 정보를 자체 완결적으로 담는다:
- **현재 코드**: 지금 레포에 있는 실제 라인
- **CodeRabbit 지적**: 무엇이 문제이고 어떻게 고치라고 했는지
- **내 평가**: 동의/부분동의/이견 + 근거
- **옵션 비교표**: 가능한 선택지와 트레이드오프
- **적용 가이드**: 선택 시 무엇을 어떻게 바꾸는가

**진행 방법**:
1. §1 요약표를 훑고 전체 그림을 잡는다
2. 카테고리(§2)별로 항목을 읽고 옵션 중 하나를 고른다
3. 결정란(`[ ]`)에 `[A]` / `[B]` / `[Skip]` 등 표기
4. §4 권장 워크플로우대로 라운드별 커밋 → CodeRabbit 재리뷰

**범례**:
- `🔴` Critical / `🟠` Major / `🟡` Minor (CodeRabbit 분류)
- `✅` 동의 / `⚠️` 부분 동의 / `❌` 이견 (내 평가)
- `⭐` 내 추천 옵션

---

## 1. 한눈에 보기

### 1.1 카테고리별 그룹핑 (MECE)

| 카테고리 | 항목 |
|---|---|
| A. 보안 | `[9]` `[14]` |
| B. 동시성 / 일관성 | `[7]` |
| C. 도메인 invariant | `[10]` `[11]` `[12]` |
| D. 예외 처리 / 로깅 | `[8]` `[15]` `[16]` |
| E. 모듈 경계 (Architecture) | `[5]` `[6]` |
| F. 테스트 품질 | `[3]` `[13]` |
| G. 설정 / 인프라 위생 | `[1]` `[2]` `[4]` |

### 1.2 결정 체크리스트

| # | 카테고리 | 한 줄 요약 | CR | 내 평가 | 결정 |
|---|---|---|---|---|---|
| 9  | A | `PasswordValidator` 생년월일 검증 우회 가능 | 🟠 | ✅ 강력 동의 | `[ ]` |
| 14 | A | `actuator/**` 전체 permitAll | 🟠 | ✅ 강력 동의 | `[ ]` |
| 7  | B | 회원가입 check-then-act race | 🟠 | ✅ 동의 | `[ ]` |
| 10 | C | `AccountName` 길이 검증 누락 | 🟠 | ✅ 동의 | `[ ]` |
| 11 | C | `CredentialIdentifier` 길이 + toString 마스킹 | 🟠 | ⚠️ 부분 동의 | `[ ]` |
| 12 | C | `Email` 길이 + toString 마스킹 | 🟠 | ⚠️ 부분 동의 | `[ ]` |
| 8  | D | Unauthorized 변환 시 cause 손실 | 🟡 | ⚠️ 부분 동의 | `[ ]` |
| 15 | D | 4xx 예외에 stacktrace 로깅 | 🟠 | ✅ 동의 | `[ ]` |
| 16 | D | 검증 예외 핸들러 부재 + `Throwable` 광역 | 🟠 | ✅ 강력 동의 | `[ ]` |
| 5  | E | `account-application` → persistence Gradle dep | 🟠 | ✅ 동의 (별도 PR) | `[ ]` |
| 6  | E | `AccountService`가 persistence 타입 import | 🟠 | ✅ 동의 (`[5]`와 묶음) | `[ ]` |
| 3  | F | 컨트롤러 테스트에 `@Transactional` 누락 | 🟡 | ❌ 이견 | `[ ]` |
| 13 | F | `PasswordValidatorTest` 케이스 설계 결함 | 🟡 | ✅ 강력 동의 | `[ ]` |
| 1  | G | `profiles.active: local` 하드코딩 | 🟠 | ⚠️ 부분 동의 | `[ ]` |
| 2  | G | `logging.yml` / `monitoring.yml` 미존재 | 🔴 | ❌ False alarm (해결됨) | `[N/A]` |
| 4  | G | docker compose 비밀번호 하드코딩 | 🔴 | ❌ 이견 | `[ ]` |

---

## 2. 카테고리별 상세 분석

---

### A. 보안

#### [9] `PasswordValidator`: 생년월일 포함 검증 우회 가능

| 위치 | severity | effort | 결정 |
|---|---|---|---|
| `modules/account-domain/src/main/kotlin/com/loopers/account/domain/validator/PasswordValidator.kt:28-29` | 🟠 Major | quick (~30m) | `[ ]` |

**현재 코드**
```kotlin
if (rawPassword.contains(birthDate.format(BIRTH_DATE_FORMATTER).substring(2))) {
    throw BadRequestException(AccountErrorCode.INVALID_PASSWORD)
}
```
- `BIRTH_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE` (yyyyMMdd) → `substring(2)`로 yyMMdd만 사용
- 1996-01-01 → `"960101"`

**CodeRabbit 지적**
- 비밀번호 전체 문자열에서 `contains("960101")`만 검사 → dash/공백 섞이면 우회됨
- 다만 현재 `ALLOWED_PASSWORD_PATTERN = ^[!-~]{8,16}$` 가 ASCII 범위만 허용하므로 dash(`-`)는 통과 가능 (`-`는 ASCII 0x2D)
- `ab1996-01-01!` 같은 입력 → length 13자(OK), 문자(OK), `contains("960101")` 검사에서는 `1996-01-01`에 `960101`이 직접 들어있지 않음 → **우회 성공**
- 권장: 비밀번호에서 **숫자만 추출**한 뒤 `yyyyMMdd`, `yyMMdd`(필요 시 `MMdd`)를 각각 검사

**내 평가** — ✅ **강력 동의**
정책의 본질(생일은 약한 비밀번호)을 우회하는 진짜 결함이다. dash 외에도 공백·언더스코어 등 ASCII 특수문자가 들어가면 같은 우회가 가능. 보안 정책은 **의도를 우회하지 못하게** 해야지, 표면적 문자열 비교로 만족하면 안 된다.

**옵션 비교**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. 숫자만 추출 후 `yyyyMMdd` + `yyMMdd` 검사 | 우회 차단, 단순 | `MMdd` 검사 없음 (1년 다른 사람은 통과) | ⭐ |
| B. A + `MMdd`까지 검사 | 더 엄격 | 0101 등 흔한 4자리는 무관한 통과를 잡아 false positive | |
| C. 현 상태 유지 | 변경 없음 | 보안 결함 | |

**적용 가이드 (A)**
```kotlin
val birthDateDigits = birthDate.format(BIRTH_DATE_FORMATTER) // yyyyMMdd
val passwordDigits = rawPassword.filter(Char::isDigit)
val yyMMdd = birthDateDigits.substring(2)

if (passwordDigits.contains(birthDateDigits) || passwordDigits.contains(yyMMdd)) {
    throw BadRequestException(AccountErrorCode.INVALID_PASSWORD)
}
```
- 추가 테스트: `ab1996-01-01!`, `x960101!a` 모두 `BadRequestException` 던지는지

---

#### [14] `AccountSecurityConfig`: `/actuator/**` 전체 permitAll

| 위치 | severity | effort | 결정 |
|---|---|---|---|
| `modules/account-security/src/main/kotlin/com/loopers/account/security/AccountSecurityConfig.kt:28-29` | 🟠 Major | quick (~20m) | `[ ]` |

**현재 코드**
```kotlin
.requestMatchers(HttpMethod.POST, ACCOUNTS_PATH).permitAll()
.requestMatchers(ACTUATOR_PATH, SWAGGER_UI_PATH, API_DOCS_PATH).permitAll()
// ...
private const val ACTUATOR_PATH = "/actuator/**"
```

**CodeRabbit 지적**
- `/actuator/**` 전체가 익명 공개 → 운영에서 `management.endpoints.web.exposure.include`가 `env`/`metrics` 등으로 확대되면 즉시 노출
- `/actuator/health` (필요 시 `/actuator/info`)만 공개, 나머지는 인증 요구

**내 평가** — ✅ **강력 동의**
지금은 노출 endpoint가 기본값(`health`, `info`)이라 실제 위험은 없지만, **path 자체를 좁히는 게 fail-safe**. 운영 환경에서 누가 exposure 설정만 바꿔도 즉시 노출되는 구조는 위험. 보안은 path 매처로 1차 방어, exposure 설정으로 2차 방어 — defense in depth.

**옵션 비교**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. `/health`, `/info`만 공개 | 표준 베스트 프랙티스 | 운영 도구가 다른 endpoint 호출 시 인증 필요 | ⭐ |
| B. `/health`만 공개 | 가장 안전 | `info` 사용 시 인증 필요 | |
| C. 현 상태 유지 | 변경 없음 | 위험 잠재 | |

**적용 가이드 (A)**
```kotlin
.requestMatchers(ACTUATOR_HEALTH_PATH, ACTUATOR_INFO_PATH, SWAGGER_UI_PATH, API_DOCS_PATH).permitAll()
// ...
private const val ACTUATOR_HEALTH_PATH = "/actuator/health"
private const val ACTUATOR_INFO_PATH = "/actuator/info"
```
- 보안 통합 테스트 추가: `GET /actuator/health` → 200, `GET /actuator/env`·`/actuator/metrics` → 401/403

---

### B. 동시성 / 일관성

#### [7] 회원가입: `existsBy` 사전 체크는 race condition 취약

| 위치 | severity | effort | 결정 |
|---|---|---|---|
| `modules/account-application/src/main/kotlin/com/loopers/account/application/AccountService.kt:37-42` | 🟠 Major | medium (~40m) | `[ ]` |

**현재 코드**
```kotlin
if (accountCredentialRepository.existsBy(CredentialMethod.PASSWORD, identifier)) {
    throw ConflictException(AccountErrorCode.DUPLICATE_LOGIN_ID)
}
if (accountRepository.existsByEmail(email)) {
    throw ConflictException(AccountErrorCode.DUPLICATE_EMAIL)
}
// save() ...
```

**CodeRabbit 지적**
- 동일 `loginId` / `email`로 동시 요청이 들어오면 둘 다 `existsBy` 통과 후 저장 시도
- DB 제약 위반 시 `DataIntegrityViolationException`이 그대로 전파되어 500 응답
- 권장: DB unique 제약 + 저장 시점 예외 → `ConflictException` 변환

**내 평가** — ✅ **동의**
운영 관점에서 정확. 단 학습 과제 컨텍스트에서 "동시성 통합 테스트까지 추가"는 과할 수 있어, **변환 로직만 추가**하는 게 실용적. 사전 체크는 fast-fail 용도로 유지(가독성·대부분 케이스 처리).

**선결 확인**
- `Account.email` (`@Column(length=255)` 있음) 와 `AccountCredential.identifier`에 **DB unique 제약(`@Column(unique=true)` 또는 `@Table(uniqueConstraints=...)`)이 있는지** 확인 필요. 없으면 먼저 추가해야 변환 로직이 의미 있음.

**옵션 비교**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. unique 제약 + `existsBy` 제거 + save 시 변환 | 가장 깔끔 | 정상 경로에서도 save 후 catch 발생 가능 | |
| B. unique 제약 + `existsBy` 유지 + save catch (fast path + safety net) | 대부분 케이스는 사전체크, race만 catch로 봉인 | 코드 두 군데 | ⭐ |
| C. Skip | 변경 없음 | race 시 500 응답 잔존 | |

**적용 가이드 (B)**
1. 엔티티에 unique 제약 추가:
   ```kotlin
   @Table(name = "account", uniqueConstraints = [UniqueConstraint(columnNames = ["email"])])
   ```
2. `AccountService.create()`의 save 두 곳을 try-catch로 감쌈:
   ```kotlin
   try { accountRepository.save(...) }
   catch (e: DataIntegrityViolationException) {
       throw ConflictException(AccountErrorCode.DUPLICATE_EMAIL) // 어느 제약인지 message로 분기
   }
   ```
3. 두 unique 제약이 있을 땐 message/SQLException SQLState로 분기 필요 → 복잡하면 `existsBy`로 사후 확인 후 분기도 가능

---

### C. 도메인 invariant

#### [10] `AccountName`: 길이 상한 검증 누락

| 위치 | severity | effort | 결정 |
|---|---|---|---|
| `modules/account-domain/src/main/kotlin/com/loopers/account/domain/vo/AccountName.kt:12-20` | 🟠 Major | quick (~15m) | `[ ]` |

**현재 코드**
```kotlin
@Column(name = "name", nullable = false, length = 100)
var value: String = value
    private set

init {
    if (value.isBlank()) {
        throw BadRequestException(AccountErrorCode.INVALID_ACCOUNT_NAME)
    }
}
```

**CodeRabbit 지적**
- 컬럼 길이 100인데 init에선 공백만 검증
- 101자 입력 시 저장 단계에서 DB 예외 → 500 응답

**내 평가** — ✅ **동의**
도메인 invariant는 객체 생성 시점에 강제하는 게 정석 (fail-fast, DDD). DB 제약은 마지막 안전망이지 1차 방어가 아니다.

**옵션 비교**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. init에 `length > 100` 검증 추가 | 명확, 즉시 효과 | — | ⭐ |
| B. Skip | 변경 없음 | DB까지 가서 깨짐 | |

**적용 가이드 (A)**
```kotlin
init {
    if (value.isBlank() || value.length > 100) {
        throw BadRequestException(AccountErrorCode.INVALID_ACCOUNT_NAME)
    }
}
```
- 테스트 추가: 100자 통과, 101자 `BadRequestException`

---

#### [11] `CredentialIdentifier`: 길이 검증 + `toString` 마스킹

> 이 항목은 **두 sub-issue**가 한 코멘트에 묶여 있다. 별도 결정 필요.

| 위치 | severity | effort | 결정 (길이) | 결정 (toString) |
|---|---|---|---|---|
| `modules/account-domain/src/main/kotlin/com/loopers/account/domain/vo/CredentialIdentifier.kt:14-31` | 🟠 Major | quick | `[ ]` | `[ ]` |

**현재 코드**
```kotlin
@Column(name = "identifier", nullable = false, length = 255)
var value: String = value
    private set

init {
    if (method == CredentialMethod.PASSWORD && !PASSWORD_IDENTIFIER_REGEX.matches(value)) {
        throw BadRequestException(AccountErrorCode.INVALID_CREDENTIAL_IDENTIFIER)
    }
}
// ...
override fun toString(): String = value
```

**CodeRabbit 지적**
1. 길이 255 상한이 init에서 검증되지 않음
2. `toString()`이 원문 식별자를 반환 → 로그 노출 위험. `"[PROTECTED]"`로 변경 권장

**내 평가**
- **길이 검증**: ✅ 동의 ([10]과 동일 논리)
- **toString 마스킹**: ⚠️ 부분 동의 / 신중 필요
  - `"[PROTECTED]"` 무조건 반환은 디버깅 시 매우 불편 (테스트 실패 메시지, IntelliJ 디버거에서 값 안 보임)
  - 로그 노출 방지는 **로깅 레이어**에서 처리하는 게 표준 (logback masking pattern, MDC filter)
  - 도메인 객체 책임 vs 로깅 인프라 책임의 경계 문제

**옵션 비교 (toString)**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. `"[PROTECTED]"` 무조건 (CodeRabbit) | 단순, 노출 차단 확실 | 디버깅 매우 불편 | |
| B. 부분 마스킹 (`abc***`) | 디버깅 가능 + 노출 최소화 | 마스킹 규칙 정의 필요 | |
| C. 원문 유지 + 로깅 레이어에서 마스킹 (logback pattern) | 표준 분리 | 로깅 설정 필요 | ⭐ |
| D. Skip toString 변경 | 변경 없음 | 노출 위험 잔존 | |

**적용 가이드 (길이만 적용)**
```kotlin
init {
    if (value.length > 255) {
        throw BadRequestException(AccountErrorCode.INVALID_CREDENTIAL_IDENTIFIER)
    }
    if (method == CredentialMethod.PASSWORD && !PASSWORD_IDENTIFIER_REGEX.matches(value)) {
        throw BadRequestException(AccountErrorCode.INVALID_CREDENTIAL_IDENTIFIER)
    }
}
```
- 단, `PASSWORD_IDENTIFIER_REGEX = ^[A-Za-z0-9]+$`로 이미 ASCII 64자 미만 보통이라 실질 효과는 작음 — 그래도 invariant 명시 가치

---

#### [12] `Email`: 길이 검증 + `toString` 마스킹

| 위치 | severity | effort | 결정 (길이) | 결정 (toString) |
|---|---|---|---|---|
| `modules/account-domain/src/main/kotlin/com/loopers/account/domain/vo/Email.kt:12-29` | 🟠 Major | quick | `[ ]` | `[ ]` |

**현재 코드**
```kotlin
@Column(name = "email", nullable = false, length = 255)
var value: String = value
    private set

init {
    if (!EMAIL_REGEX.matches(value)) {
        throw BadRequestException(AccountErrorCode.INVALID_EMAIL)
    }
}
// ...
override fun toString(): String = value
```

**CodeRabbit 지적 / 내 평가**: `[11]`과 구조 동일. 동일한 결정 로직 적용.

**적용 가이드 (길이만 적용)**
```kotlin
init {
    if (value.length > 255 || !EMAIL_REGEX.matches(value)) {
        throw BadRequestException(AccountErrorCode.INVALID_EMAIL)
    }
}
```
- RFC 5321 기준 email max는 254자라 사실상 255면 충분

---

### D. 예외 처리 / 로깅

#### [8] `Unauthorized` 변환 시 cause 손실

| 위치 | severity | effort | 결정 |
|---|---|---|---|
| `modules/account-application/src/main/kotlin/com/loopers/account/application/AccountService.kt:117-121` | 🟡 Minor | quick (~10m) | `[ ]` |

**현재 코드**
```kotlin
private fun createPasswordIdentifier(loginId: String): CredentialIdentifier =
    try {
        CredentialIdentifier(CredentialMethod.PASSWORD, loginId)
    } catch (e: BadRequestException) {
        throw UnauthorizedException()
    }
```

**CodeRabbit 지적**
- `BadRequestException`을 잡아서 정보 손실. detekt도 swallowed exception 경고
- cause를 보존하거나, 식별자 형식 오류만 401로 분기하라

**내 평가** — ⚠️ **부분 동의**
- 401로 통일하는 **의도는 정당** (보안: "이 ID는 형식이 잘못됨" → ID 추측 공격에 단서 제공). 인증 응답을 401로 통일하는 게 OWASP authentication 권장
- 하지만 **원인 추적용 cause는 보존**하는 게 좋다. 운영 로그에서 `UnauthorizedException`이 왔을 때 원인이 무엇인지 차이가 큼

**옵션 비교**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. CodeRabbit 안 (분기): 식별자 형식 오류만 401, 다른 BadRequest는 재throw | 의미 명확 | 분기 코드 복잡 | |
| B. cause만 보존: `UnauthorizedException(cause = e)` | 단순, 정보 보존 | `UnauthorizedException` 생성자 수정 필요 (cause 받게) | ⭐ |
| C. 현 상태 유지 | 변경 없음 | 정보 손실, detekt 경고 | |

**적용 가이드 (B)**
1. `UnauthorizedException`이 cause를 받는 생성자가 없으면 추가:
   ```kotlin
   class UnauthorizedException(errorCode: ErrorCode = CommonErrorCode.UNAUTHORIZED, cause: Throwable? = null)
       : CoreException(errorCode, cause)
   ```
2. 호출부:
   ```kotlin
   catch (e: BadRequestException) {
       throw UnauthorizedException(cause = e)
   }
   ```
3. 로깅 정책에서 5xx만 stacktrace 출력하면 식별자 노출 위험 없음 (관련 `[15]`)

---

#### [15] `CoreException` 4xx도 stacktrace 로깅

| 위치 | severity | effort | 결정 |
|---|---|---|---|
| `supports/web/src/main/kotlin/com/loopers/interfaces/api/ApiControllerAdvice.kt:29-33` | 🟠 Major | quick (~20m) | `[ ]` |

**현재 코드**
```kotlin
@ExceptionHandler
fun handle(e: CoreException): ResponseEntity<ApiResponse<*>> {
    log.warn("CoreException : {}", e.message, e)  // e를 throwable로 전달 → stacktrace
    return failureResponse(e)
}
```

**CodeRabbit 지적**
- 4xx 클라이언트 오류까지 stacktrace 누적 → 로그 노이즈 + 민감정보(메시지 내 입력값) 노출
- 4xx는 구조화 로그(코드/상태)만, 5xx만 stacktrace

**내 평가** — ✅ **동의**
운영 로그 품질 + 보안 측면에서 명확한 개선. 4xx는 정상 사용자 오류, 5xx만 진짜 장애.

**옵션 비교**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. status별 분기 (4xx warn 메시지만, 5xx error+stacktrace) | 표준 패턴 | 코드 약간 늘어남 | ⭐ |
| B. 모두 stacktrace 제거 | 단순 | 5xx 디버깅 어려움 | |
| C. 현 상태 유지 | 변경 없음 | 노이즈/위험 | |

**적용 가이드 (A)**
```kotlin
@ExceptionHandler
fun handle(e: CoreException): ResponseEntity<ApiResponse<*>> {
    val status = resolveStatus(e)
    if (status.is5xxServerError) {
        log.error("CoreException [{}] {}", e.errorCode.code, e.message, e)
    } else {
        log.warn("CoreException [{}] {}", e.errorCode.code, e.message)
    }
    return failureResponse(e)
}
```

---

#### [16] 검증 예외 핸들러 부재 + `Throwable` 광역 핸들러

| 위치 | severity | effort | 결정 |
|---|---|---|---|
| `supports/web/src/main/kotlin/com/loopers/interfaces/api/ApiControllerAdvice.kt:67-71` | 🟠 Major | quick (~30m) | `[ ]` |

**현재 코드**
```kotlin
@ExceptionHandler
fun handle(e: Throwable): ResponseEntity<ApiResponse<*>> {
    log.error("Exception : {}", e.message, e)
    return failureResponse(InternalServerException())
}
```

**현재 상태 보강 정보** (코드 재확인 결과):
- `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `HttpMessageNotReadableException`, `ServerWebInputException` 핸들러는 **이미 존재** → MVC 입력 파싱 단계 검증은 400으로 매핑됨
- **누락**: `MethodArgumentNotValidException` (`@Valid` 바인딩 검증 실패), `BindException`, `ConstraintViolationException` (`@Validated` + JSR-380 메서드 파라미터 검증)
- 현재 컨트롤러에 `@Valid` / `@Validated` **사용 흔적 없음** → 즉시 효과는 없지만, 향후 검증 도입 시 즉시 500으로 떨어짐

**CodeRabbit 지적**
1. `MethodArgumentNotValidException` / `BindException` / `ConstraintViolationException` 전용 핸들러 추가 → 400
2. `Throwable` → `Exception`으로 narrowing (Error/OOM은 catch 안 함)

**내 평가** — ✅ **강력 동의**
- 향후 `@Valid` 도입 안전망 + `Throwable` narrowing은 best practice. JVM `Error` (OutOfMemoryError, StackOverflowError 등)을 catch하면 안 됨

**옵션 비교**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. 둘 다 적용 (검증 핸들러 추가 + Throwable→Exception) | 완전한 fix | — | ⭐ |
| B. `Throwable`→`Exception` narrowing만 | 작은 변경 | 향후 `@Valid` 시 누락 | |
| C. 검증 핸들러만 추가 | 즉시 효과 작음 | `Error` 잡는 문제 잔존 | |
| D. Skip | 변경 없음 | 잠재 위험 | |

**적용 가이드 (A)**
```kotlin
import jakarta.validation.ConstraintViolationException
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException

@ExceptionHandler(
    MethodArgumentNotValidException::class,
    BindException::class,
    ConstraintViolationException::class,
)
fun handleValidation(e: Exception): ResponseEntity<ApiResponse<*>> =
    failureResponse(BadRequestException(CommonErrorCode.BAD_REQUEST, e.message ?: ""))

@ExceptionHandler
fun handle(e: Exception): ResponseEntity<ApiResponse<*>> {
    log.error("Exception : {}", e.message, e)
    return failureResponse(InternalServerException())
}
```
- 테스트 추가: `@Valid` 사용하는 임시 컨트롤러로 MockMvc 400 응답 확인 — 또는 이번엔 핸들러만 추가하고 테스트는 `@Valid` 도입 시점에

---

### E. 모듈 경계 (Clean Architecture)

> `[5]` `[6]`은 같은 구조적 문제의 두 측면. 함께 해결.

#### [5][6] `account-application`이 `account-persistence`에 직접 의존

| 위치 | severity | effort | 결정 |
|---|---|---|---|
| `modules/account-application/build.gradle.kts:2-4` <br> `modules/account-application/src/main/kotlin/com/loopers/account/application/AccountService.kt:13-14, 25-26` | 🟠 Major | heavy (~3-4h) | `[ ]` |

**현재 코드**

`build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":modules:account-domain"))
    implementation(project(":modules:account-persistence"))  // ← 위반
    implementation(project(":supports:error"))
    implementation("org.springframework:spring-tx")
}
```

`AccountService.kt`:
```kotlin
import com.loopers.account.persistence.AccountCredentialRepository  // ← 위반
import com.loopers.account.persistence.AccountRepository            // ← 위반
// ...
class AccountService(
    private val accountRepository: AccountRepository,
    private val accountCredentialRepository: AccountCredentialRepository,
    // ...
)
```

**CodeRabbit 지적**
- Repository port 인터페이스를 `account-domain`(혹은 application port 계층)에 두고, `account-persistence`는 그 port의 adapter로만
- `account-application` 의존성은 domain + error 중심으로 축소

**AGENTS.md §12 Module Boundaries 명시 (위반 확인)**:
> `account-domain` — entities, VO, validators, **repository ports**, `PasswordEncryptor`
> `account-persistence` — Spring Data JPA 리포지토리 + **port 어댑터**

**내 평가** — ✅ **동의** (구조적 결함이지만 별도 PR 권장)
프로젝트 규칙과 정확히 어긋남. 다만 작업량이 크다:
1. `AccountRepository`, `AccountCredentialRepository` 인터페이스를 `account-domain`으로 이동
2. `account-persistence`에는 인터페이스 구현체(`AccountRepositoryImpl` 또는 Spring Data 직접 구현)
3. DI 재배선 + 패키지 import 정리
4. application 테스트는 mock만으로 가능해야 함 (현재 가능한지 확인 필요)
5. Gradle 의존성에서 `:modules:account-persistence` 제거 → application 단독 컴파일 가능 여부 검증

**옵션 비교**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. 이 PR에 포함 | 한 번에 정리 | 리뷰 어려움, PR 크기 폭증 | |
| B. 별도 follow-up PR | 리뷰 용이, 다른 fix와 분리 | 두 PR 관리 | ⭐ |
| C. Skip (학습 종료) | 변경 없음 | 규칙 위반 잔존 | |

**적용 가이드 (B — 별도 PR)**
1. 현재 PR(`assignment/week-01-member-account`)을 먼저 머지
2. 새 브랜치 `refactor/account-module-boundary`에서:
   - `AccountRepository`, `AccountCredentialRepository`를 `com.loopers.account.domain.repository`로 이동
   - `account-persistence`에 Spring Data 기반 구현체 작성 (`@Repository` 또는 `JpaRepository` 위임)
   - `AccountService` import 변경
   - `account-application/build.gradle.kts`에서 `:modules:account-persistence` 제거
   - `apps/account-api/build.gradle.kts`에서 `:modules:account-persistence` 추가 (런타임 어댑터로)
3. 아키텍처 테스트(`ArchUnit`) 추가하여 의존성 방향 고정

---

### F. 테스트 품질

#### [3] `AccountControllerTest`에 `@Transactional` 누락

| 위치 | severity | effort | 결정 |
|---|---|---|---|
| `apps/account-api/src/test/kotlin/com/loopers/account/api/AccountControllerTest.kt:23-30` | 🟡 Minor | quick~medium | `[ ]` |

**현재 코드**
```kotlin
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AccountControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
    private val accountService: AccountService,
    private val jdbcTemplate: JdbcTemplate,
) {
```

**CodeRabbit 지적**
- 클래스에 `@Transactional` 없어 테스트 간 데이터 격리 안 됨
- `accountService.create()` 호출 후 데이터가 남아 후속 테스트와 충돌 가능

**내 평가** — ❌ **이견** (단순 추가는 best practice 아님)
- `@SpringBootTest` + JPA에 무조건 `@Transactional`은 **함정**: lazy loading 문제를 가리고, MockMvc 요청은 별도 트랜잭션을 만들 수 있어 롤백이 안 먹힐 수 있음
- `@AutoConfigureMockMvc(addFilters=false)`로 MockMvc 호출 자체는 같은 트랜잭션 안에서 돌아서 그나마 작동할 가능성 높지만, **권장 패턴은 아님**
- `JdbcTemplate`이 이미 주입돼 있는 걸 보면 **명시적 cleanup** 의도가 있어 보임

**옵션 비교**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. `@Transactional` 추가 (CodeRabbit 안) | 단순 | lazy 가림, MockMvc 트랜잭션 경계 함정 | |
| B. `@AfterEach` cleanup 메서드 | 명시적, 안전 | 클래스마다 작성 | |
| C. `DatabaseCleanup` 유틸 클래스 도입 (테이블 truncate + ID reset) | 재사용 가능, 명시적 | 초기 구현 필요 | ⭐ |
| D. Skip | 변경 없음 | 잠재 충돌 | |

**적용 가이드 (C)**
```kotlin
// supports/web/src/testFixtures (또는 별도 모듈)
@Component
class DatabaseCleanup(private val em: EntityManager) : InitializingBean {
    private lateinit var tableNames: List<String>
    override fun afterPropertiesSet() {
        tableNames = em.metamodel.entities
            .filter { it.javaType.isAnnotationPresent(Entity::class.java) }
            .map { /* @Table.name 또는 snake_case 변환 */ }
    }
    @Transactional
    fun execute() {
        em.flush()
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate()
        tableNames.forEach {
            em.createNativeQuery("TRUNCATE TABLE $it").executeUpdate()
            em.createNativeQuery("ALTER TABLE $it ALTER COLUMN id RESTART WITH 1").executeUpdate()
        }
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate()
    }
}
```
```kotlin
class AccountControllerTest : @Autowired constructor(
    // ...
    private val databaseCleanup: DatabaseCleanup,
) {
    @AfterEach fun cleanup() = databaseCleanup.execute()
}
```

**옵션 B (간이판)**
```kotlin
@AfterEach
fun cleanup() {
    jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE")
    jdbcTemplate.execute("TRUNCATE TABLE account_credential")
    jdbcTemplate.execute("TRUNCATE TABLE account")
    jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE")
}
```

---

#### [13] `PasswordValidatorTest` 케이스 설계 결함

| 위치 | severity | effort | 결정 |
|---|---|---|---|
| `modules/account-domain/src/test/kotlin/com/loopers/account/domain/validator/PasswordValidatorTest.kt:44-52` | 🟡 Minor | quick (~10m) | `[ ]` |

**현재 코드**
```kotlin
@Test
@DisplayName("영문 대소문자, 숫자, 특수문자 이외의 문자가 포함된 비밀번호는 사용할 수 없다")
fun wrongWithNotAllowedCharacterPassword() {
    val wrongPassword = "a1wbc123  !1231adsf"  // 길이 19자 → length 규칙에서 먼저 실패

    assertThrows<BadRequestException> {
        PasswordValidator.validate(wrongPassword, birthDate)
    }
}
```

**CodeRabbit 지적**
- 19자 → length 규칙(8-16)에서 먼저 실패. character rule 회귀를 못 잡음
- 8-16자 범위 내 공백/비ASCII 케이스로 교체

**내 평가** — ✅ **강력 동의**
명백한 테스트 의도 오류. character 분기가 죽어도 통과한다 → 회귀 안전망 무력화.

**옵션 비교**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. CodeRabbit 안: `"ab12 cd!@#"` (공백 포함, 10자) | 즉시 fix | 케이스 1개만 | |
| B. A + 한글 포함 케이스 추가 (`@ParameterizedTest` 또는 별 테스트) | MECE한 회귀 탐지 | 약간 더 작성 | ⭐ |

**적용 가이드 (B)**
```kotlin
@ParameterizedTest
@ValueSource(strings = ["ab12 cd!@#", "ab12한글!@", "ab12cd€!@"])
@DisplayName("영문 대소문자, 숫자, 특수문자 이외의 문자가 포함된 비밀번호는 사용할 수 없다")
fun wrongWithNotAllowedCharacterPassword(wrongPassword: String) {
    assertThrows<BadRequestException> {
        PasswordValidator.validate(wrongPassword, birthDate)
    }
}
```

---

### G. 설정 / 인프라 위생

#### [1] `application.yaml` `profiles.active: local` 하드코딩

| 위치 | severity | effort | 결정 |
|---|---|---|---|
| `apps/account-api/src/main/resources/application.yaml:4-5` | 🟠 Major | quick | `[ ]` |

**현재 코드**
```yaml
spring:
  application:
    name: account-api
  profiles:
    active: local
  config:
    import:
      - jpa.yml
      - logging.yml
      - monitoring.yml
```

**CodeRabbit 지적**
- 운영/스테이징 환경 매번 오버라이드 필요
- 환경변수(`SPRING_PROFILES_ACTIVE`) 또는 JVM 인자(`-Dspring.profiles.active`)로 주입

**내 평가** — ⚠️ **부분 동의 (학습 컨텍스트엔 우선순위 낮음)**
원칙적으로는 맞음. 다만 이 PR은 학습 과제로 운영 배포 인프라가 없고, default를 명시하지 않으면 IntelliJ Run config나 매번 -D 옵션을 줘야 해서 onboarding 마찰이 증가. **학습 단계에선 받아들이는 비용 > 얻는 가치**.

**옵션 비교**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. `profiles.active` 제거 + IntelliJ Run Config로 옮김 | 표준 | onboarding 마찰 (Run config commit 안 하면 매번 setup) | |
| B. `profiles.active` 제거 + Gradle bootRun args에 default | 빌드 도구로 관리 | gradle 모를 때 혼란 | |
| C. 현 상태 유지 | onboarding 쉬움 | 운영 시 override 필요 | ⭐ (학습 컨텍스트) |
| D. 빈 문자열로 (`active: ${SPRING_PROFILES_ACTIVE:local}`) | 명시적 default + 외부 주입 가능 | 의미 자체는 A와 같음 | |

**적용 가이드 (Skip 시)**
- 변경 없음. PR 코멘트에 "학습 컨텍스트, 운영 배포 없음 → 의도적 보류"라 회신

---

#### [2] `logging.yml` / `monitoring.yml` 미존재 — **N/A (false alarm)**

| 위치 | severity | 상태 |
|---|---|---|
| `apps/account-api/src/main/resources/application.yaml:7-10` | 🔴 Critical | ❌ **해결됨** |

**파일 위치 확인**
- `jpa.yml` → `modules/jpa/src/main/resources/jpa.yml`
- `logging.yml` → `supports/logging/src/main/resources/logging.yml`
- `monitoring.yml` → `supports/monitoring/src/main/resources/monitoring.yml`

세 파일 모두 클래스패스(다른 모듈의 resources)에 존재. Spring `spring.config.import`는 클래스패스 전체에서 import 가능 — CodeRabbit의 `fd` 검색 범위가 좁아서 발생한 **false alarm**.

**적용 가이드**: 무시. PR 코멘트에 회신 불필요 (CodeRabbit이 자동 마감하지 않으면 해결됨 표시만).

---

#### [4] `compose.yaml` 비밀번호 하드코딩

| 위치 | severity | effort | 결정 |
|---|---|---|---|
| `compose.yaml:6-10` | 🔴 Critical | quick | `[ ]` |

**현재 코드**
```yaml
environment:
  MYSQL_DATABASE: loopers
  MYSQL_USER: application
  MYSQL_PASSWORD: application
  MYSQL_ROOT_PASSWORD: root
```

**CodeRabbit 지적**
- "Do not commit secrets" 규칙 위반
- `.env` 외부화 + `.gitignore` 추가 + `.env.example` 제공

**내 평가** — ❌ **이견** (Critical 분류 과도)
- AGENTS.md/CLAUDE.md의 "Do not commit secrets"는 **진짜 시크릿** (운영 DB 비밀번호, API 키, 인증 토큰)을 의미
- 로컬 docker compose의 `application`/`root` 같은 기본값은 **시크릿이 아니다**. 누구나 README에서 봐도 OK
- Critical 분류는 과도 (regular 패턴, 학습 프로젝트 컨텍스트)

**옵션 비교**

| 옵션 | 장점 | 단점 | 추천 |
|---|---|---|---|
| A. `.env` 외부화 (CodeRabbit 안) | 회사 컨벤션 일부와 일치, 운영 시 자연스러운 전환 | 학습 마찰 ↑ | |
| B. 현 상태 유지 + README/AGENTS.md에 "로컬 전용" 한 줄 명시 | onboarding 쉬움 + 명확성 | 외부화는 안 됨 | ⭐ |
| C. 현 상태 유지 (변경 없음) | 최소 변경 | — | |

**적용 가이드 (Skip 시)**
- PR 코멘트에 "로컬 전용 default. 실제 시크릿 아님. AGENTS.md의 '시크릿 커밋 금지' 규칙 범위 밖이라 판단" 회신

---

## 3. 우선순위 매트릭스 (Severity × Effort)

|         | Quick win (≤30m)              | Medium (30-60m)              | Heavy (1h+)                  |
|---------|-------------------------------|------------------------------|------------------------------|
| **🔴 보안** | `[9]` 비번 우회 / `[14]` actuator | —                            | —                            |
| **🟠 운영** | `[16]` 검증 핸들러 / `[15]` 4xx log | `[7]` race 봉인              | —                            |
| **🟠 일관성** | `[10]` AccountName 길이 / `[11]` CI 길이 / `[12]` Email 길이 | — | `[5][6]` 모듈 경계 (별도 PR) |
| **🟡 품질** | `[13]` test case / `[8]` cause 보존 | `[3]` cleanup 유틸 (옵션 C) | —                            |
| **⚪ 정책** | — | `[1]` profiles.active | — |
| **❌ Skip** | `[2]` 해결됨 / `[4]` 시크릿 아님 / `[11]` toString / `[12]` toString | — | — |

---

## 4. 권장 워크플로우

### Round 1 — 보안/명백 결함 (P0, ~1.5h)
**커밋 메시지**: `fix: address security and validation findings from coderabbit review`

대상:
- `[9]` PasswordValidator 숫자 추출 비교
- `[13]` PasswordValidatorTest 케이스 수정
- `[14]` actuator path 좁히기 + 보안 통합 테스트
- `[16]` 검증 예외 핸들러 추가 + Throwable→Exception
- `[10]` AccountName 길이 검증 + 경계 테스트

후속: CodeRabbit 재리뷰 트리거 (`@coderabbitai review`)

### Round 2 — 운영 견고성 (P1, ~1.5h)
**커밋 메시지**: `refactor: tighten domain invariants and exception handling`

대상:
- `[7]` 회원가입 unique constraint + DataIntegrityViolation 변환 (먼저 unique 제약 확인)
- `[11]` CredentialIdentifier 길이 검증 (toString은 Skip)
- `[12]` Email 길이 검증 (toString은 Skip)
- `[15]` ApiControllerAdvice 4xx stacktrace 제거
- `[8]` UnauthorizedException cause 보존

### Round 3 — 구조적 리팩터링 (P2, 별도 PR)
**브랜치**: `refactor/account-module-boundary`
**대상**: `[5][6]` Repository port를 `account-domain`으로 이동, persistence는 adapter로

### Round 4 — Skip 정리 (PR 코멘트 회신)
다음 항목은 PR 코멘트에 "의도적 보류" 회신:
- `[1]` 학습 컨텍스트, 운영 배포 없음
- `[2]` false alarm — 해당 파일들은 `supports/*` 모듈 resources에 존재
- `[3]` `@Transactional` 단순 추가 대신 별도 cleanup 패턴 (옵션 C가 따로 시점)
- `[4]` 로컬 docker default는 시크릿 범위 밖
- `[11]` `[12]` toString 마스킹 — 로깅 레이어 정책으로 처리 예정

---

## 5. 부록: 항목별 원본 링크

| # | 원본 코멘트 |
|---|---|
| 1  | [r3252357399](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357399) |
| 2  | [r3252357408](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357408) |
| 3  | [r3252357411](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357411) |
| 4  | [r3252357413](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357413) |
| 5  | [r3252357414](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357414) |
| 6  | [r3252357416](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357416) |
| 7  | [r3252357419](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357419) |
| 8  | [r3252357421](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357421) |
| 9  | [r3252357424](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357424) |
| 10 | [r3252357427](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357427) |
| 11 | [r3252357430](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357430) |
| 12 | [r3252357433](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357433) |
| 13 | [r3252357435](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357435) |
| 14 | [r3252357437](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357437) |
| 15 | [r3252357439](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357439) |
| 16 | [r3252357441](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/12#discussion_r3252357441) |
