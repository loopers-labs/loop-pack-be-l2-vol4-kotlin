# Architecture Conformance High Medium Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the high and medium `docs/architecture.md` conformance violations found in application code, excluding the low-priority `LocalDateTime` timestamp issue.

**Architecture:** Move use-case orchestration and transactions from `domain` to `application`, so HTTP controllers and auth support call application services instead of domain services. Keep domain focused on entities, value objects, repository ports, and domain invariants. Use JPA soft-delete annotations and explicit metadata methods, and use pessimistic write locking for password-change concurrency.

**Tech Stack:** Kotlin 2.0.20, Spring Boot 3.4.4, Spring Data JPA, Hibernate annotations, JUnit 5, AssertJ, Testcontainers MySQL through existing `modules:jpa` fixtures.

---

## Scope And Assumptions

- Fix only the high and medium violations from the audit:
  - controller/auth direct domain service use
  - `@Transactional` in domain services
  - `BaseEntity` lifecycle callbacks
  - missing soft-delete filtering/delete SQL
  - read path returning domain entities instead of application read models
  - password-change concurrency without locking
- Do not fix the low timestamp-type issue in this plan.
- Use pessimistic locking only where there is an active write race in current code: password change.
- Keep the HTTP JSON shape stable. `role` continues to serialize as `"CONSUMER"` or `"ADMIN"`, but controller DTOs should type it as `String` to avoid importing the domain enum into the API layer.
- The query read port for `ExampleInfo` will live in `application.example`. This is the smallest way to satisfy the architecture rule that read queries return application read models. If the team later wants a stricter port-location rule, update `docs/architecture.md` first.

## File Structure

- Create `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserApplicationService.kt`: user registration, authentication, and password-change use cases with transaction boundaries.
- Create `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserCommand.kt`: application-layer user input commands.
- Create `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserInfo.kt`: application-layer user read model used by controllers and auth support.
- Delete `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt`: orchestration and transaction code no longer belongs in domain.
- Delete `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt`: controller DTOs should map to application commands, not domain commands.
- Modify `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRepository.kt`: add pessimistic-lock lookup port.
- Modify `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserJpaRepository.kt`: add `PESSIMISTIC_WRITE` query.
- Modify `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserRepositoryImpl.kt`: call explicit metadata methods before save and implement locked lookup.
- Modify `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/*`: use application service, application command/info, and no domain entity/service imports.
- Modify `apps/commerce-api/src/main/kotlin/com/loopers/support/auth/*`: use `UserApplicationService` and `UserInfo`, not domain service/entity.
- Create `apps/commerce-api/src/main/kotlin/com/loopers/application/example/ExampleQueryRepository.kt`: read-only query port returning `ExampleInfo`.
- Modify `apps/commerce-api/src/main/kotlin/com/loopers/application/example/ExampleFacade.kt`: own read transaction and call query port.
- Modify or rename `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/example/ExampleRepositoryImpl.kt`: implement read query port and return `ExampleInfo`.
- Delete `apps/commerce-api/src/main/kotlin/com/loopers/domain/example/ExampleService.kt` and `apps/commerce-api/src/main/kotlin/com/loopers/domain/example/ExampleRepository.kt`: unused after CQRS read path is moved.
- Modify `modules/jpa/src/main/kotlin/com/loopers/domain/BaseEntity.kt`: remove lifecycle callbacks and expose explicit metadata methods.
- Modify `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/User.kt` and `apps/commerce-api/src/main/kotlin/com/loopers/domain/example/ExampleModel.kt`: add `@SQLDelete`, `@SQLRestriction`, and required soft-delete warning comments.
- Move `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceTest.kt` to `apps/commerce-api/src/test/kotlin/com/loopers/application/user/UserApplicationServiceTest.kt`.
- Move `apps/commerce-api/src/test/kotlin/com/loopers/domain/example/ExampleServiceIntegrationTest.kt` to `apps/commerce-api/src/test/kotlin/com/loopers/application/example/ExampleFacadeIntegrationTest.kt`.
- Create `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/UserRepositoryImplIntegrationTest.kt`: soft-delete behavior.
- Create `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/UserJpaRepositoryLockTest.kt`: repository lock annotation.
- Create `modules/jpa/src/test/kotlin/com/loopers/domain/BaseEntityTest.kt`: lifecycle callback absence and explicit metadata behavior.

### Task 1: Move User Use Cases To Application Layer

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserCommand.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserInfo.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserApplicationService.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1ApiSpec.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/AdminUserV1Controller.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/support/auth/AuthenticationInterceptor.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/support/auth/CurrentUserArgumentResolver.kt`
- Move: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceTest.kt` to `apps/commerce-api/src/test/kotlin/com/loopers/application/user/UserApplicationServiceTest.kt`
- Delete: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt`
- Delete: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt`

- [ ] **Step 1: Move and edit the service test first**

Run:

```bash
mkdir -p apps/commerce-api/src/test/kotlin/com/loopers/application/user
git mv apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceTest.kt apps/commerce-api/src/test/kotlin/com/loopers/application/user/UserApplicationServiceTest.kt
```

Edit the moved test with these exact structural changes:

```kotlin
package com.loopers.application.user

import com.loopers.domain.user.FakePasswordEncoder
import com.loopers.domain.user.FakeUserRepository
import com.loopers.domain.user.RawPassword
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserApplicationServiceTest {
    private val repository = FakeUserRepository()
    private val passwordEncoder = FakePasswordEncoder()
    private val service = UserApplicationService(repository, passwordEncoder)
}
```

In the moved file, apply these exact replacements before running tests:

```text
UserServiceTest -> UserApplicationServiceTest
UserService(repository, passwordEncoder) -> UserApplicationService(repository, passwordEncoder)
UserRole.CONSUMER -> "CONSUMER"
UserRole.ADMIN -> "ADMIN"
service.authenticate("loopers01", RawPassword("abcd1234")) -> service.authenticate("loopers01", "abcd1234")
service.authenticate("nobody", RawPassword("abcd1234")) -> service.authenticate("nobody", "abcd1234")
service.authenticate("loopers01", RawPassword("wxyz5678")) -> service.authenticate("loopers01", "wxyz5678")
service.changePassword(user.id, RawPassword("abcd1234"), RawPassword("wxyz5678")) -> service.changePassword(user.id, "abcd1234", "wxyz5678")
service.changePassword(user.id, RawPassword("wrongold"), RawPassword("wxyz5678")) -> service.changePassword(user.id, "wrongold", "wxyz5678")
service.changePassword(user.id, RawPassword("abcd1234"), RawPassword("abcd1234")) -> service.changePassword(user.id, "abcd1234", "abcd1234")
service.changePassword(user.id, RawPassword("abcd1234"), RawPassword("ab19900101")) -> service.changePassword(user.id, "abcd1234", "ab19900101")
```

For the `storesEncryptedPasswordNotRaw` test, use this assertion body because application methods return `UserInfo`:

```kotlin
val saved = service.register(
    UserCommand.Register(
        loginId = "loopers01",
        rawPassword = "abcd1234",
        name = "홍길동",
        birthdate = LocalDate.of(1990, 1, 1),
        email = "user@example.com",
    ),
)
val stored = repository.findByLoginId(saved.loginId)!!

assertAll(
    { assertThat(stored.encryptedPassword).isNotEqualTo("abcd1234") },
    { assertThat(passwordEncoder.matches(RawPassword("abcd1234"), stored.encryptedPassword)).isTrue() },
)
```

For the password-change test calls, use raw strings:

```kotlin
service.changePassword(user.id, "abcd1234", "wxyz5678")
```

- [ ] **Step 2: Run the moved user application test and verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.user.UserApplicationServiceTest"
```

Expected: FAIL at compile time with unresolved references for `UserApplicationService` and `com.loopers.application.user.UserCommand`.

- [ ] **Step 3: Add application command and info types**

Create `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserCommand.kt`:

```kotlin
package com.loopers.application.user

import java.time.LocalDate

class UserCommand {
    data class Register(
        val loginId: String,
        val rawPassword: String,
        val name: String,
        val birthdate: LocalDate,
        val email: String,
    )
}
```

Create `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserInfo.kt`:

```kotlin
package com.loopers.application.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRole
import java.time.LocalDate

data class UserInfo(
    val id: Long,
    val loginId: String,
    val name: String,
    val birthdate: LocalDate,
    val email: String,
    val role: String,
) {
    fun isAdmin(): Boolean = role == UserRole.ADMIN.name

    companion object {
        fun from(user: User): UserInfo =
            UserInfo(
                id = user.id,
                loginId = user.loginId,
                name = user.name,
                birthdate = user.birthdate,
                email = user.email,
                role = user.role.name,
            )
    }
}
```

- [ ] **Step 4: Create `UserApplicationService`**

Create `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserApplicationService.kt`:

```kotlin
package com.loopers.application.user

import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.UserRole
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class UserApplicationService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun register(command: UserCommand.Register): UserInfo = register(command, UserRole.CONSUMER)

    @Transactional
    fun registerAdmin(command: UserCommand.Register): UserInfo = register(command, UserRole.ADMIN)

    private fun register(command: UserCommand.Register, role: UserRole): UserInfo {
        if (userRepository.existsByLoginId(command.loginId)) {
            throw CoreException(ErrorType.CONFLICT, "이미 사용 중인 loginId 입니다.")
        }

        val rawPassword = RawPassword(command.rawPassword)
        rejectIfPasswordContainsBirthdate(rawPassword, command.birthdate)

        val user = User(
            loginId = command.loginId,
            encryptedPassword = passwordEncoder.encode(rawPassword),
            name = command.name,
            birthdate = command.birthdate,
            email = command.email,
            role = role,
        )
        return UserInfo.from(userRepository.save(user))
    }

    @Transactional(readOnly = true)
    fun authenticate(loginId: String, rawPassword: String): UserInfo {
        val password = RawPassword(rawPassword)
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.")
        if (!passwordEncoder.matches(password, user.encryptedPassword)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        return UserInfo.from(user)
    }

    @Transactional
    fun changePassword(userId: Long, oldPassword: String, newPassword: String) {
        val oldRawPassword = RawPassword(oldPassword)
        val newRawPassword = RawPassword(newPassword)
        val user = userRepository.findById(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.")

        if (!passwordEncoder.matches(oldRawPassword, user.encryptedPassword)) {
            throw CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.")
        }
        if (passwordEncoder.matches(newRawPassword, user.encryptedPassword)) {
            throw CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 달라야 합니다.")
        }
        rejectIfPasswordContainsBirthdate(newRawPassword, user.birthdate)
        user.changePassword(passwordEncoder.encode(newRawPassword))
    }

    private fun rejectIfPasswordContainsBirthdate(password: RawPassword, birthdate: LocalDate) {
        val forbidden = listOf(
            birthdate.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            birthdate.format(DateTimeFormatter.ofPattern("yyMMdd")),
            birthdate.format(DateTimeFormatter.ofPattern("MMdd")),
        )
        val digitGroups = Regex("\\d+").findAll(password.value).map { it.value }
        if (digitGroups.any { group -> forbidden.any { group.contains(it) } }) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일이 포함될 수 없습니다.")
        }
    }
}
```

- [ ] **Step 5: Update API DTOs to depend on application types**

In `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt`, replace the imports and conversion target:

```kotlin
package com.loopers.interfaces.api.user

import com.loopers.application.user.UserCommand
import com.loopers.application.user.UserInfo
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
```

Use this `SignUpRequest.toCommand()`:

```kotlin
fun toCommand(): UserCommand.Register = UserCommand.Register(
    loginId = loginId,
    rawPassword = password,
    name = name,
    birthdate = birthdate,
    email = email,
)
```

Use this response type:

```kotlin
data class MyInfoResponse(
    val loginId: String,
    val name: String,
    val birthdate: LocalDate,
    val email: String,
    val role: String,
) {
    companion object {
        fun from(user: UserInfo): MyInfoResponse = MyInfoResponse(
            loginId = user.loginId,
            name = maskName(user.name),
            birthdate = user.birthdate,
            email = user.email,
            role = user.role,
        )

        private fun maskName(name: String): String = if (name.length <= 1) name else name.dropLast(1) + "*"
    }
}
```

- [ ] **Step 6: Update user controllers and API specs**

In `UserV1Controller.kt`, use application types:

```kotlin
package com.loopers.interfaces.api.user

import com.loopers.application.user.UserApplicationService
import com.loopers.application.user.UserInfo
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.CurrentUser
import com.loopers.support.auth.LoginRequired
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userApplicationService: UserApplicationService,
) : UserV1ApiSpec {
    @PostMapping
    override fun signUp(
        @RequestBody @Valid request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.MyInfoResponse> {
        val user = userApplicationService.register(request.toCommand())
        return ApiResponse.success(UserV1Dto.MyInfoResponse.from(user))
    }

    @LoginRequired
    @GetMapping("/me")
    override fun getMyInfo(
        @CurrentUser user: UserInfo,
    ): ApiResponse<UserV1Dto.MyInfoResponse> = ApiResponse.success(UserV1Dto.MyInfoResponse.from(user))

    @LoginRequired
    @PatchMapping("/me/password")
    override fun changePassword(
        @CurrentUser user: UserInfo,
        @RequestBody @Valid request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Unit> {
        userApplicationService.changePassword(user.id, request.oldPassword, request.newPassword)
        return ApiResponse.success(Unit)
    }
}
```

In `UserV1ApiSpec.kt`, use `UserInfo`:

```kotlin
package com.loopers.interfaces.api.user

import com.loopers.application.user.UserInfo
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "Loopers 사용자 API 입니다.")
interface UserV1ApiSpec {
    @Operation(summary = "회원가입", description = "신규 사용자를 생성합니다.")
    fun signUp(request: UserV1Dto.SignUpRequest): ApiResponse<UserV1Dto.MyInfoResponse>

    @Operation(summary = "내 정보 조회", description = "헤더 인증 후 본인 정보를 반환합니다.")
    fun getMyInfo(user: UserInfo): ApiResponse<UserV1Dto.MyInfoResponse>

    @Operation(summary = "비밀번호 변경", description = "기존 비밀번호 확인 후 비밀번호를 변경합니다.")
    fun changePassword(user: UserInfo, request: UserV1Dto.ChangePasswordRequest): ApiResponse<Unit>
}
```

In `AdminUserV1Controller.kt`, inject `UserApplicationService`:

```kotlin
package com.loopers.interfaces.api.user

import com.loopers.application.user.UserApplicationService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.Admin
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserV1Controller(
    private val userApplicationService: UserApplicationService,
) : AdminUserV1ApiSpec {
    @Admin
    @PostMapping
    override fun signUpAdmin(
        @RequestBody @Valid request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.MyInfoResponse> =
        userApplicationService.registerAdmin(request.toCommand())
            .let { UserV1Dto.MyInfoResponse.from(it) }
            .let { ApiResponse.success(it) }
}
```

- [ ] **Step 7: Update auth support to use application user info**

In `AuthenticationInterceptor.kt`, use this implementation:

```kotlin
package com.loopers.support.auth

import com.loopers.application.user.UserApplicationService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuthenticationInterceptor(
    private val userApplicationService: UserApplicationService,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true

        val requiresAdmin =
            handler.method.isAnnotationPresent(Admin::class.java) ||
                handler.beanType.isAnnotationPresent(Admin::class.java)
        val requiresLogin =
            handler.method.isAnnotationPresent(LoginRequired::class.java) ||
                handler.beanType.isAnnotationPresent(LoginRequired::class.java) ||
                requiresAdmin
        if (!requiresLogin) return true

        val loginId = request.getHeader(LOGIN_ID_HEADER)
        val loginPw = request.getHeader(LOGIN_PW_HEADER)
        if (loginId.isNullOrBlank() || loginPw.isNullOrBlank()) {
            throw CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 필요합니다.")
        }

        val user = userApplicationService.authenticate(loginId, loginPw)
        request.setAttribute(CURRENT_USER_KEY, user)
        if (requiresAdmin && !user.isAdmin()) {
            throw CoreException(ErrorType.FORBIDDEN, "Admin role is required.")
        }
        return true
    }

    companion object {
        const val LOGIN_ID_HEADER = "X-Loopers-LoginId"
        const val LOGIN_PW_HEADER = "X-Loopers-LoginPw"
        const val CURRENT_USER_KEY = "com.loopers.support.auth.CURRENT_USER"
    }
}
```

In `CurrentUserArgumentResolver.kt`, support `UserInfo`:

```kotlin
package com.loopers.support.auth

import com.loopers.application.user.UserInfo
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentUser::class.java) &&
            UserInfo::class.java.isAssignableFrom(parameter.parameterType)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
            ?: error("HttpServletRequest 가 NativeWebRequest 에 없습니다.")
        return request.getAttribute(AuthenticationInterceptor.CURRENT_USER_KEY)
            ?: error("@CurrentUser 가 @LoginRequired 없는 핸들러에서 사용되었습니다.")
    }
}
```

- [ ] **Step 8: Delete obsolete domain orchestration files**

Run:

```bash
git rm apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt
git rm apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserCommand.kt
```

- [ ] **Step 9: Run user tests**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.user.UserApplicationServiceTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.user.UserV1ApiE2ETest"
./gradlew :apps:commerce-api:test --tests "com.loopers.support.auth.AuthenticationInterceptorTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.support.auth.CurrentUserArgumentResolverTest"
```

Before running, update API response role assertions in `UserV1ApiE2ETest.kt` to strings:

```kotlin
assertThat(response.body?.data?.role).isEqualTo("CONSUMER")
assertThat(response.body?.data?.role).isEqualTo("ADMIN")
```

Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/user \
  apps/commerce-api/src/main/kotlin/com/loopers/domain/user \
  apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user \
  apps/commerce-api/src/main/kotlin/com/loopers/support/auth \
  apps/commerce-api/src/test/kotlin/com/loopers/application/user \
  apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/user \
  apps/commerce-api/src/test/kotlin/com/loopers/support/auth
git commit -m "refactor: move user use cases to application layer"
```

### Task 2: Move Example Read Path To Application CQRS Query

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/application/example/ExampleQueryRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/example/ExampleFacade.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/example/ExampleRepositoryImpl.kt`
- Move: `apps/commerce-api/src/test/kotlin/com/loopers/domain/example/ExampleServiceIntegrationTest.kt` to `apps/commerce-api/src/test/kotlin/com/loopers/application/example/ExampleFacadeIntegrationTest.kt`
- Delete: `apps/commerce-api/src/main/kotlin/com/loopers/domain/example/ExampleService.kt`
- Delete: `apps/commerce-api/src/main/kotlin/com/loopers/domain/example/ExampleRepository.kt`

- [ ] **Step 1: Move and edit the example integration test**

Run:

```bash
mkdir -p apps/commerce-api/src/test/kotlin/com/loopers/application/example
git mv apps/commerce-api/src/test/kotlin/com/loopers/domain/example/ExampleServiceIntegrationTest.kt apps/commerce-api/src/test/kotlin/com/loopers/application/example/ExampleFacadeIntegrationTest.kt
```

Replace the moved file contents:

```kotlin
package com.loopers.application.example

import com.loopers.domain.example.ExampleModel
import com.loopers.infrastructure.example.ExampleJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ExampleFacadeIntegrationTest @Autowired constructor(
    private val exampleFacade: ExampleFacade,
    private val exampleJpaRepository: ExampleJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("예시를 조회할 때,")
    @Nested
    inner class Get {
        @DisplayName("존재하는 예시 ID를 주면, 해당 예시 정보를 반환한다.")
        @Test
        fun returnsExampleInfo_whenValidIdIsProvided() {
            val exampleModel = exampleJpaRepository.save(ExampleModel(name = "예시 제목", description = "예시 설명").apply { createMeta() })

            val result = exampleFacade.getExample(exampleModel.id)

            assertAll(
                { assertThat(result.id).isEqualTo(exampleModel.id) },
                { assertThat(result.name).isEqualTo(exampleModel.name) },
                { assertThat(result.description).isEqualTo(exampleModel.description) },
            )
        }

        @DisplayName("존재하지 않는 예시 ID를 주면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsException_whenInvalidIdIsProvided() {
            val exception = assertThrows<CoreException> {
                exampleFacade.getExample(999L)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
```

- [ ] **Step 2: Run the moved example test and verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.example.ExampleFacadeIntegrationTest"
```

Expected: FAIL while the facade still depends on `domain.example.ExampleService`.

- [ ] **Step 3: Add the application read port**

Create `apps/commerce-api/src/main/kotlin/com/loopers/application/example/ExampleQueryRepository.kt`:

```kotlin
package com.loopers.application.example

interface ExampleQueryRepository {
    fun findInfo(id: Long): ExampleInfo?
}
```

- [ ] **Step 4: Update the facade to own the read transaction**

Replace `apps/commerce-api/src/main/kotlin/com/loopers/application/example/ExampleFacade.kt`:

```kotlin
package com.loopers.application.example

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ExampleFacade(
    private val exampleQueryRepository: ExampleQueryRepository,
) {
    @Transactional(readOnly = true)
    fun getExample(id: Long): ExampleInfo {
        return exampleQueryRepository.findInfo(id)
            ?: throw CoreException(errorType = ErrorType.NOT_FOUND, customMessage = "[id = $id] 예시를 찾을 수 없습니다.")
    }
}
```

- [ ] **Step 5: Update infrastructure query implementation**

Replace `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/example/ExampleRepositoryImpl.kt`:

```kotlin
package com.loopers.infrastructure.example

import com.loopers.application.example.ExampleInfo
import com.loopers.application.example.ExampleQueryRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ExampleRepositoryImpl(
    private val exampleJpaRepository: ExampleJpaRepository,
) : ExampleQueryRepository {
    override fun findInfo(id: Long): ExampleInfo? {
        return exampleJpaRepository.findByIdOrNull(id)
            ?.let { ExampleInfo.from(it) }
    }
}
```

- [ ] **Step 6: Delete obsolete domain read service and repository**

Run:

```bash
git rm apps/commerce-api/src/main/kotlin/com/loopers/domain/example/ExampleService.kt
git rm apps/commerce-api/src/main/kotlin/com/loopers/domain/example/ExampleRepository.kt
```

- [ ] **Step 7: Run example tests**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.example.ExampleFacadeIntegrationTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ExampleV1ApiE2ETest"
```

Expected: PASS. If `ExampleV1ApiE2ETest` fails after lifecycle callbacks are removed in Task 3, update its setup to save examples with `.apply { createMeta() }`.

- [ ] **Step 8: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/application/example \
  apps/commerce-api/src/main/kotlin/com/loopers/domain/example \
  apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/example \
  apps/commerce-api/src/test/kotlin/com/loopers/application/example \
  apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/ExampleV1ApiE2ETest.kt
git commit -m "refactor: route example reads through application query"
```

### Task 3: Replace JPA Lifecycle Callbacks With Explicit Metadata And Soft Delete

**Files:**
- Modify: `modules/jpa/src/main/kotlin/com/loopers/domain/BaseEntity.kt`
- Create: `modules/jpa/src/test/kotlin/com/loopers/domain/BaseEntityTest.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/User.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/example/ExampleModel.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserRepositoryImpl.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/UserRepositoryImplIntegrationTest.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/ExampleV1ApiE2ETest.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/user/UserV1ApiE2ETest.kt`

- [ ] **Step 1: Add BaseEntity tests**

Create `modules/jpa/src/test/kotlin/com/loopers/domain/BaseEntityTest.kt`:

```kotlin
package com.loopers.domain

import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BaseEntityTest {
    @Test
    fun doesNotUseJpaLifecycleCallbacks() {
        val annotationTypes = BaseEntity::class.java.declaredMethods
            .flatMap { method -> method.annotations.map { it.annotationClass.java } }

        assertThat(annotationTypes).doesNotContain(PrePersist::class.java, PreUpdate::class.java)
    }

    @Test
    fun createMetaInitializesCreatedAtAndUpdatedAtExplicitly() {
        val entity = TestEntity()

        entity.createMeta()

        assertThat(entity.createdAt).isNotNull()
        assertThat(entity.updatedAt).isNotNull()
    }

    private class TestEntity : BaseEntity()
}
```

- [ ] **Step 2: Run BaseEntity tests and verify they fail**

Run:

```bash
./gradlew :modules:jpa:test --tests "com.loopers.domain.BaseEntityTest"
```

Expected: FAIL because `BaseEntity` still has `@PrePersist` and `@PreUpdate`.

- [ ] **Step 3: Replace BaseEntity implementation**

Replace `modules/jpa/src/main/kotlin/com/loopers/domain/BaseEntity.kt`:

```kotlin
package com.loopers.domain

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.time.ZonedDateTime

/**
 * 생성/수정/삭제 정보를 명시적으로 관리한다.
 * 재사용성을 위해 이 외의 컬럼이나 동작은 추가하지 않는다.
 *
 * @property id 엔티티 ID
 * @property createdAt 생성 시점
 * @property updatedAt 수정 시점
 * @property deletedAt 삭제 시점
 */
@MappedSuperclass
abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: ZonedDateTime
        protected set

    @Column(name = "deleted_at")
    var deletedAt: ZonedDateTime? = null
        protected set

    open fun guard() = Unit

    fun createMeta(now: ZonedDateTime = ZonedDateTime.now()) {
        guard()
        if (!::createdAt.isInitialized) {
            createdAt = now
        }
        updatedAt = now
    }

    fun updateMeta(now: ZonedDateTime = ZonedDateTime.now()) {
        guard()
        if (!::createdAt.isInitialized) {
            createdAt = now
        }
        updatedAt = now
    }

    fun delete(now: ZonedDateTime = ZonedDateTime.now()) {
        deletedAt ?: run {
            deletedAt = now
            updateMeta(now)
        }
    }

    fun restore(now: ZonedDateTime = ZonedDateTime.now()) {
        deletedAt?.let {
            deletedAt = null
            updateMeta(now)
        }
    }
}
```

- [ ] **Step 4: Add soft-delete annotations to entities**

In `User.kt`, add imports:

```kotlin
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
```

Replace the entity annotations:

```kotlin
@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
// JPA filters soft-deleted rows only through Hibernate. JDBC/batch queries for users must add deleted_at IS NULL and soft-delete update SQL.
class User(
```

In `ExampleModel.kt`, add imports:

```kotlin
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
```

Replace the entity annotations:

```kotlin
@Entity
@Table(name = "example")
@SQLDelete(sql = "UPDATE example SET deleted_at = NOW(), updated_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
// JPA filters soft-deleted rows only through Hibernate. JDBC/batch queries for example must add deleted_at IS NULL and soft-delete update SQL.
class ExampleModel(
```

- [ ] **Step 5: Make repository save call metadata explicitly**

Replace `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserRepositoryImpl.kt`:

```kotlin
package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun findByLoginId(loginId: String): User? = userJpaRepository.findByLoginId(loginId)

    override fun existsByLoginId(loginId: String): Boolean = userJpaRepository.existsByLoginId(loginId)

    override fun findById(id: Long): User? = userJpaRepository.findByIdOrNull(id)

    override fun save(user: User): User {
        if (user.id == 0L) {
            user.createMeta()
        } else {
            user.updateMeta()
        }
        return userJpaRepository.save(user)
    }
}
```

- [ ] **Step 6: Add soft-delete integration test**

Create `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/UserRepositoryImplIntegrationTest.kt`:

```kotlin
package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.UserRole
import com.loopers.utils.DatabaseCleanUp
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class UserRepositoryImplIntegrationTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val userJpaRepository: UserJpaRepository,
    private val entityManager: EntityManager,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("JpaRepository delete는 users row를 soft delete하고 조회에서 제외한다.")
    @Test
    fun softDeletesAndFiltersUser() {
        val saved = userRepository.save(
            User(
                loginId = "loopers01",
                encryptedPassword = "encoded-password",
                name = "홍길동",
                birthdate = LocalDate.of(1990, 1, 1),
                email = "user@example.com",
                role = UserRole.CONSUMER,
            ),
        )

        userJpaRepository.delete(saved)
        userJpaRepository.flush()
        entityManager.clear()

        val rawDeletedRows = (entityManager
            .createNativeQuery("SELECT COUNT(*) FROM users WHERE login_id = 'loopers01' AND deleted_at IS NOT NULL")
            .singleResult as Number).toLong()

        assertAll(
            { assertThat(userRepository.findByLoginId("loopers01")).isNull() },
            { assertThat(rawDeletedRows).isEqualTo(1L) },
        )
    }
}
```

- [ ] **Step 7: Update direct JPA test setup saves**

In `ExampleV1ApiE2ETest.kt`, change direct example creation to:

```kotlin
val exampleModel = exampleJpaRepository.save(ExampleModel(name = "예시 제목", description = "예시 설명").apply { createMeta() })
```

In `UserV1ApiE2ETest.kt`, change `saveAdmin()` to save a user with explicit metadata:

```kotlin
private fun saveAdmin() {
    userJpaRepository.save(
        User(
            loginId = "admin01",
            encryptedPassword = passwordEncoder.encode(RawPassword("admin1234")),
            name = "Admin",
            birthdate = LocalDate.of(1988, 8, 8),
            email = "admin@example.com",
            role = UserRole.ADMIN,
        ).apply { createMeta() },
    )
}
```

- [ ] **Step 8: Run metadata and soft-delete tests**

Run:

```bash
./gradlew :modules:jpa:test --tests "com.loopers.domain.BaseEntityTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.user.UserRepositoryImplIntegrationTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ExampleV1ApiE2ETest"
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.user.UserV1ApiE2ETest"
```

Expected: PASS. These app tests require Docker because the JPA test fixture starts MySQL through Testcontainers.

- [ ] **Step 9: Commit**

```bash
git add modules/jpa/src/main/kotlin/com/loopers/domain/BaseEntity.kt \
  modules/jpa/src/test/kotlin/com/loopers/domain/BaseEntityTest.kt \
  apps/commerce-api/src/main/kotlin/com/loopers/domain/user/User.kt \
  apps/commerce-api/src/main/kotlin/com/loopers/domain/example/ExampleModel.kt \
  apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserRepositoryImpl.kt \
  apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/UserRepositoryImplIntegrationTest.kt \
  apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/ExampleV1ApiE2ETest.kt \
  apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/user/UserV1ApiE2ETest.kt
git commit -m "refactor: make entity metadata and soft delete explicit"
```

### Task 4: Add Pessimistic Locking For Password Change

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserJpaRepository.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserRepositoryImpl.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserApplicationService.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/FakeUserRepository.kt`
- Modify: `apps/commerce-api/src/test/kotlin/com/loopers/application/user/UserApplicationServiceTest.kt`
- Create: `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/UserJpaRepositoryLockTest.kt`

- [ ] **Step 1: Add a service test for locked lookup**

In `UserApplicationServiceTest.kt`, add this test inside `ChangePassword`:

```kotlin
@DisplayName("비밀번호 변경 대상 사용자를 비관적 락 조회로 가져온다.")
@Test
fun usesPessimisticLockLookupWhenChangingPassword() {
    val user = registerDefault()

    service.changePassword(user.id, "abcd1234", "wxyz5678")

    assertThat(repository.lockedFindIds).containsExactly(user.id)
}
```

- [ ] **Step 2: Run the service test and verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.user.UserApplicationServiceTest"
```

Expected: FAIL because `FakeUserRepository.lockedFindIds` and the locked repository method do not exist yet.

- [ ] **Step 3: Extend repository port and fake**

Replace `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRepository.kt`:

```kotlin
package com.loopers.domain.user

interface UserRepository {
    fun findByLoginId(loginId: String): User?

    fun existsByLoginId(loginId: String): Boolean

    fun findById(id: Long): User?

    fun findByIdWithPessimisticLock(id: Long): User?

    fun save(user: User): User
}
```

Replace `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/FakeUserRepository.kt`:

```kotlin
package com.loopers.domain.user

import java.util.concurrent.atomic.AtomicLong

class FakeUserRepository : UserRepository {
    private val store = mutableMapOf<Long, User>()
    private val sequence = AtomicLong(1)
    val lockedFindIds = mutableListOf<Long>()

    override fun findByLoginId(loginId: String): User? = store.values.firstOrNull { it.loginId == loginId }

    override fun existsByLoginId(loginId: String): Boolean = store.values.any { it.loginId == loginId }

    override fun findById(id: Long): User? = store[id]

    override fun findByIdWithPessimisticLock(id: Long): User? {
        lockedFindIds += id
        return findById(id)
    }

    override fun save(user: User): User {
        val currentId = idField.getLong(user)
        val isNew = currentId == 0L
        val id = if (isNew) {
            sequence.getAndIncrement().also { idField.setLong(user, it) }
        } else {
            currentId
        }
        if (isNew) {
            user.createMeta()
        } else {
            user.updateMeta()
        }
        store[id] = user
        return user
    }

    companion object {
        private val idField = com.loopers.domain.BaseEntity::class.java.getDeclaredField("id").apply { isAccessible = true }
    }
}
```

- [ ] **Step 4: Add JPA pessimistic-lock query**

Replace `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserJpaRepository.kt`:

```kotlin
package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserJpaRepository : JpaRepository<User, Long> {
    fun findByLoginId(loginId: String): User?

    fun existsByLoginId(loginId: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun findByIdWithPessimisticLock(@Param("id") id: Long): User?
}
```

Update `UserRepositoryImpl.kt` by adding:

```kotlin
override fun findByIdWithPessimisticLock(id: Long): User? = userJpaRepository.findByIdWithPessimisticLock(id)
```

- [ ] **Step 5: Use the locked lookup in password change**

In `UserApplicationService.changePassword`, replace:

```kotlin
val user = userRepository.findById(userId)
    ?: throw CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.")
```

with:

```kotlin
val user = userRepository.findByIdWithPessimisticLock(userId)
    ?: throw CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.")
```

- [ ] **Step 6: Add repository annotation test**

Create `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/UserJpaRepositoryLockTest.kt`:

```kotlin
package com.loopers.infrastructure.user

import jakarta.persistence.LockModeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.Lock

class UserJpaRepositoryLockTest {
    @DisplayName("findByIdWithPessimisticLock uses PESSIMISTIC_WRITE.")
    @Test
    fun findByIdWithPessimisticLockUsesPessimisticWrite() {
        val method = UserJpaRepository::class.java.getMethod(
            "findByIdWithPessimisticLock",
            Long::class.javaPrimitiveType,
        )

        assertThat(method.getAnnotation(Lock::class.java).value).isEqualTo(LockModeType.PESSIMISTIC_WRITE)
    }
}
```

- [ ] **Step 7: Run locking tests**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.user.UserApplicationServiceTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.user.UserJpaRepositoryLockTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRepository.kt \
  apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserJpaRepository.kt \
  apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/user/UserRepositoryImpl.kt \
  apps/commerce-api/src/main/kotlin/com/loopers/application/user/UserApplicationService.kt \
  apps/commerce-api/src/test/kotlin/com/loopers/domain/user/FakeUserRepository.kt \
  apps/commerce-api/src/test/kotlin/com/loopers/application/user/UserApplicationServiceTest.kt \
  apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/user/UserJpaRepositoryLockTest.kt
git commit -m "feat: lock user password changes pessimistically"
```

### Task 5: Architecture Conformance Verification

**Files:**
- Verify only; no planned source edits.

- [ ] **Step 1: Confirm no application transaction annotations remain in domain main code**

Run:

```bash
rg -n "@Transactional" apps/commerce-api/src/main/kotlin/com/loopers/domain modules/jpa/src/main/kotlin --glob '*.kt'
```

Expected: no output.

- [ ] **Step 2: Confirm controllers no longer import domain user service/entity/command**

Run:

```bash
rg -n "import com\\.loopers\\.domain\\.user\\.(UserService|UserCommand|User$|RawPassword|UserRole)" apps/commerce-api/src/main/kotlin/com/loopers/interfaces apps/commerce-api/src/main/kotlin/com/loopers/support --glob '*.kt'
```

Expected: no output.

- [ ] **Step 3: Confirm lifecycle callbacks are gone**

Run:

```bash
rg -n "@PrePersist|@PreUpdate|@PreRemove|@PostPersist|@PostUpdate|@PostLoad" modules/jpa/src/main/kotlin apps/commerce-api/src/main/kotlin --glob '*.kt'
```

Expected: no output.

- [ ] **Step 4: Confirm soft-delete annotations exist on current entities**

Run:

```bash
rg -n "@SQLDelete|@SQLRestriction" apps/commerce-api/src/main/kotlin/com/loopers/domain --glob '*.kt'
```

Expected output includes:

```text
apps/commerce-api/src/main/kotlin/com/loopers/domain/user/User.kt
apps/commerce-api/src/main/kotlin/com/loopers/domain/example/ExampleModel.kt
```

- [ ] **Step 5: Run focused test suite**

Run:

```bash
./gradlew :modules:jpa:test --tests "com.loopers.domain.BaseEntityTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.application.user.UserApplicationServiceTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.application.example.ExampleFacadeIntegrationTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.user.UserRepositoryImplIntegrationTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.user.UserJpaRepositoryLockTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.user.UserV1ApiE2ETest"
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ExampleV1ApiE2ETest"
```

Expected: PASS.

- [ ] **Step 6: Run module tests and lint**

Run:

```bash
./gradlew :apps:commerce-api:test
./gradlew :modules:jpa:test
./gradlew ktlintCheck
```

Expected: PASS.

- [ ] **Step 7: Commit verification-only cleanup if needed**

If the verification commands reveal only import ordering or ktlint formatting changes, run:

```bash
./gradlew ktlintFormat
git add apps/commerce-api modules/jpa
git commit -m "style: format architecture conformance changes"
```

If `ktlintFormat` makes no changes, do not create a commit.

## Self-Review

- Spec coverage: Tasks 1 and 2 fix controller/application/domain and CQRS read-path violations; Task 3 fixes lifecycle callbacks and soft delete; Task 4 fixes password-change concurrency with pessimistic locking; Task 5 verifies no high/medium audit class remains.
- Placeholder scan: no task depends on an unspecified type or deferred implementation detail.
- Type consistency: controllers and auth use `UserInfo`; DTOs create `application.user.UserCommand`; application service uses domain entities and ports internally; repository port method is consistently named `findByIdWithPessimisticLock`.
