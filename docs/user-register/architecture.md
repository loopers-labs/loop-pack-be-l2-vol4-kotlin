# Auth — Architecture

Scope: sign-up, get-my-info, change-password endpoints, plus the header-based authentication mechanism shared by all protected endpoints.

## Goals & non-goals

- **Goals**
  - Implement sign-up, get-my-info, change-password per the feature spec.
  - Header-based stateless authentication (`X-Loopers-LoginId`, `X-Loopers-LoginPw`).
  - Keep business logic independent of the Spring framework — domain types must not import Spring.
  - Make the protected endpoint set greppable and tamper-resistant under refactors.
- **Non-goals**
  - No Spring Security. The team accepts the resulting risks (password on every request, no session, no rate limiting, no CSRF/CORS opinions baked in).
  - No facade layer for this feature. There is no cross-module orchestration to justify it; the controller talks directly to the domain service.
  - No login/logout endpoints, no token issuance, no refresh flows. Auth is "verify credentials on every request."

## Layered layout

```
interfaces/api/user/
  ├── UserV1Controller.kt
  ├── UserV1ApiSpec.kt
  └── UserV1Dto.kt

domain/user/
  ├── User.kt                  JPA entity, extends BaseEntity
  │                            fields: loginId: String, encryptedPassword: String,
  │                                    name: String, birthdate: LocalDate, email: String
  ├── UserService.kt           @Component, @Transactional
  ├── UserRepository.kt        port (interface)
  ├── PasswordEncoder.kt       port (interface)
  └── RawPassword.kt           value class — only surviving wrapper
                               (regex check on construction; toString() redacted)

infrastructure/user/
  ├── UserRepositoryImpl.kt    implements domain.user.UserRepository
  ├── UserJpaRepository.kt     Spring Data
  └── BCryptPasswordEncoder.kt implements domain.user.PasswordEncoder
                               (uses at.favre.lib:bcrypt — no Spring Security)

support/auth/
  ├── CurrentUser.kt                  parameter annotation
  ├── LoginRequired.kt                method/class annotation
  ├── AuthenticationInterceptor.kt    HandlerInterceptor — authenticates AND enforces
  ├── CurrentUserArgumentResolver.kt  HandlerMethodArgumentResolver — reads from request scope
  └── AuthWebMvcConfigurer.kt         registers interceptor + resolver
```

### Convention note on `support/`

Previously `support/` held only Spring-free code (`support/error`). With this change it becomes "cross-cutting feature packages; Spring-coupled allowed." The grouping rule is cohesion (the annotations and the Spring components they drive must move together), not Spring-coupling. Do not infer from `support/error`'s purity that `support/` is a Spring-free zone.

### Why no facade

Per project CLAUDE.md the canonical flow is `Controller → Facade → Service → Repository`. For this feature the facade would be a single-line passthrough — there is no second domain to orchestrate with. The controller depends directly on `UserService`. If future features (e.g., points, orders) need to be composed with user operations, introduce `application/user/UserFacade` then.

## Authentication pipeline

```
HTTP request
  │
  ▼
AuthenticationInterceptor.preHandle(req, resp, handler)
  ├─ if handler is not HandlerMethod → return true (static resources, error dispatches)
  ├─ inspect handler method (and its declaring class) for @LoginRequired
  │     ├─ absent → return true (public endpoint; headers ignored)
  │     └─ present → continue
  ├─ read X-Loopers-LoginId / X-Loopers-LoginPw
  │     ├─ either missing/blank → throw CoreException(UNAUTHORIZED)
  │     └─ both present → continue
  ├─ userService.authenticate(loginId, rawPw)
  │     ├─ no user / hash mismatch → throw CoreException(UNAUTHORIZED)
  │     └─ success → req.setAttribute(CURRENT_USER_KEY, user); return true
  │
  ▼
CurrentUserArgumentResolver.resolveArgument
  ├─ read req.getAttribute(CURRENT_USER_KEY)
  ├─ absent → throw IllegalStateException
  │     ("@CurrentUser used without @LoginRequired" — programming error, not 401)
  └─ present → return User
  │
  ▼
Controller method body
```

### Rules locked in

- **`@LoginRequired` is mandatory on every protected endpoint.** No exceptions. The protected set is `grep -r @LoginRequired apps/commerce-api/src/main`.
- **`@CurrentUser` is a thin reader.** It never enforces auth. Missing user in request scope = programmer error (`@CurrentUser` placed without `@LoginRequired`), not an HTTP 401.
- **Public endpoints ignore the headers entirely.** Sign-up does not attempt opportunistic authentication if headers happen to be present.
- **Unauthorized failures throw `CoreException(ErrorType.UNAUTHORIZED, …)`.** `ApiControllerAdvice` already maps `CoreException` to the standard envelope, so no custom translation is needed in `support/auth/`.

## Domain model

### `RawPassword` (the only surviving wrapper)

```kotlin
@JvmInline
value class RawPassword(val value: String) {
    init {
        require(value.matches(PATTERN)) { ... }   // throws CoreException(BAD_REQUEST)
    }
    override fun toString(): String = "***"        // never log a plaintext password
    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9!@#$%^&*()\\-_=+\\[\\]{};:'\",.<>/?\\\\|`~]{8,16}$")
    }
}
```

Two reasons it earned its place:
- `PasswordEncoder.encode(raw: RawPassword): String` self-documents direction. Plain strings can be confused; this signature can't.
- Redacted `toString()` removes a real foot-gun (accidental log lines printing the password).

### `User` entity — validates in `init`

Extends `BaseEntity` (`id`, `createdAt`, `updatedAt`, `deletedAt`). Not a `data class` (per `.coderabbit.yaml`). Field types are plain Kotlin/JDK types, not wrappers:

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
        // encryptedPassword is opaque — trust the encoder
    }

    fun changePassword(newEncrypted: String) { this.encryptedPassword = newEncrypted }
}
```

- Per-field shape rules live on `init` (`loginId` regex, `name` non-blank, `email` regex, `birthdate` past).
- Cross-field rules (birthdate-in-password) and uniqueness (`loginId`) live on `UserService` — they can't be enforced from a single object's construction.
- `User` does not validate `encryptedPassword` content; it trusts the encoder.

`init`-block failures should throw `CoreException(ErrorType.BAD_REQUEST, …)` so the existing `ApiControllerAdvice` produces the standard envelope.

### Ports

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

`infrastructure/user/BCryptPasswordEncoder` uses `at.favre.lib:bcrypt` — keeps the boundary clean with the "no Spring Security" decision.

## Birthdate-in-password rule

Enforced in `UserService` at both register and changePassword.

```
forbiddenSubstrings = [
    birthdate.format("yyyyMMdd"),
    birthdate.format("yyMMdd"),
    birthdate.format("MMdd"),
]

digitGroups = Regex("\\d+").findAll(rawPassword).map { it.value }

if any digitGroup contains any forbiddenSubstring → throw BAD_REQUEST
```

The rule is intentionally strict: for birthday `1990-01-01` (`MMdd = "0101"`), a password digit-run like `1234560101` is rejected even though `"0101"` isn't literally a date there. This is accepted false-positive risk in exchange for a simple, auditable rule.

## `changePassword` semantics

Request body carries `{ oldPassword, newPassword }`. Even though the interceptor already verified the header password, the service **re-verifies `oldPassword` from the body against the stored hash**. Rationale:

- Sudo-style re-auth: the explicit "type your current password" gesture is a meaningful user intent for a sensitive action, not theatre.
- The service contract stays clean — it doesn't need to know about HTTP headers.
- If the body's `oldPassword` and the header password disagree, the request is rejected at the service layer with `BAD_REQUEST` (no implicit trust of either side).

Additional service-layer checks at changePassword:
- `newPassword` must satisfy `RawPassword` value-object rules (shape, length).
- `newPassword` must not violate the birthdate-in-password rule.
- `newPassword` must not match the current stored password (`!encoder.matches(newPassword, user.encryptedPassword)`).

## HTTP contract (sketch)

| Method | Path | Auth | Body | Response |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/users` | public | `{ loginId, password, name, birthdate, email }` | `MyInfoResponse` |
| GET | `/api/v1/users/me` | `@LoginRequired` | — | `MyInfoResponse` |
| PATCH | `/api/v1/users/me/password` | `@LoginRequired` | `{ oldPassword, newPassword }` | empty success |

`MyInfoResponse = { loginId, name (masked), birthdate, email }`. Name masking (replace the last character with `*`) is a presentation concern, performed inline in the DTO mapper (`MyInfoResponse.from(user)`). The domain returns the full name; masking is the responsibility of whoever is rendering it.

All responses go through `ApiResponse.success(...)`. All error paths throw `CoreException(ErrorType, …)`; `ApiControllerAdvice` handles translation.

## Controller shape (illustrative)

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

## Accepted tradeoffs (write them down so we don't relitigate)

- **Per-request bcrypt verify** is expensive (~50–200ms on protected calls). Accepted as the cost of stateless header auth without sessions. A loginId → (hash, userId) Redis cache with short TTL is a future option; not done now.
- **No Spring Security.** Pulling in `spring-security-crypto` alone (just for bcrypt) would be the smallest concession; we chose `at.favre.lib:bcrypt` instead to keep the "no Spring Security" boundary unambiguous.
- **Birthdate-in-password is over-strict.** False positives (e.g., `1234560101`) are accepted in exchange for a one-line rule that's easy to audit and explain.
- **No facade.** Reintroduce one when a second domain (points/orders/etc.) needs to compose with user operations.
- **`support/` is no longer Spring-free.** Cohesion wins over coupling-axis purity for this package.

## Decisions explicitly rejected

- **`@LoginRequired` as an AOP aspect separate from the interceptor.** Rejected: split the auth decision across two places (header read in interceptor, enforcement in aspect) for no functional gain.
- **`@CurrentUser` resolver enforcing auth on its own.** Rejected: with `@LoginRequired` mandatory, the interceptor is already guaranteed to have stashed (or rejected). Duplicate enforcement creates two error paths for the same condition.
- **DTO-level business validation (`@field:Pattern` mirroring entity regex).** Rejected: two sources of truth for the same rule will drift. DTOs do shape checks (`@NotBlank` / `@NotNull` / `@Email`); the `User` entity's `init` block owns business shape; `UserService` owns cross-field rules.

- **Wrapping every field in a value object (`LoginId`, `Name`, `Email`, `Birthdate`, `EncryptedPassword`).** Rejected: six wrappers for a sign-up form is ceremony without payoff. Type safety against mixing up `name` and `email` isn't a real risk in a CRUD entity. `RawPassword` is the exception — see above. `EncryptedPassword` also dropped (kept as `String` for JPA convenience).
- **Trusting the header password as the "old password" in `changePassword`.** Rejected: makes the service contract HTTP-aware and removes a deliberate user-intent gesture.

## Open items (for implementation, not architecture)

- Exact column mapping strategy for value objects (`@Embedded` vs scalar columns + `@Convert`).
- Bean Validation message bundle setup (i18n out of scope, but error message strings need consistency).
- Test fixtures for `User` via Instancio — define a builder helper alongside `domain/user/` test sources.
