# User Register Admin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the existing `commerce-api` user registration and header-auth slice with `docs/user-register/architecture.md` by completing role-backed admin creation and authorization.

**Architecture:** Keep the no-facade shape chosen by the architecture doc: controllers call `UserService` directly, and `UserService` owns registration, authentication, and password-change rules. Persist `UserRole` as a non-null enum string on `User`; public sign-up creates `CONSUMER`, admin sign-up creates `ADMIN`, and `AuthenticationInterceptor` owns both authentication and `@Admin` authorization.

**Tech Stack:** Kotlin 2.0.20, JDK 21, Spring Boot 3.4.4 Web/JPA, Gradle Kotlin DSL, JUnit 5, AssertJ, Spring `TestRestTemplate`, Testcontainers MySQL, `at.favre.lib:bcrypt`.

---

## Assumptions

- The current code already implements public sign-up, get-my-info, change-password, `RawPassword`, bcrypt encoding, `@LoginRequired`, and `@CurrentUser`.
- This plan is a delta plan against the current repository, not a greenfield rewrite.
- No first-admin bootstrap endpoint will be created. Tests that need an existing admin will seed one directly with `UserJpaRepository` and the domain `PasswordEncoder`.
- The enum column strategy is fixed here as scalar JPA mapping: `@Enumerated(EnumType.STRING)` on `User.role`. No `@Embedded` or converter is needed because only `RawPassword` remains as a value wrapper and it is never persisted.
- The worktree was dirty when this plan was written. Before executing, run `git status --short` and do not revert unrelated user changes.

## Success Criteria

- Public `POST /api/v1/users` returns a `MyInfoResponse` with `role = CONSUMER`.
- `POST /api/v1/admin/users` exists, is protected by `@Admin`, ignores any role choice from the request body, and creates `role = ADMIN`.
- Invalid admin credentials return 401; authenticated non-admin users on admin endpoints return 403.
- `GET /api/v1/users/me` and `PATCH /api/v1/users/me/password` keep their existing behavior.
- The protected endpoint set remains greppable with:

```bash
grep -rE "@LoginRequired|@Admin" apps/commerce-api/src/main
```

- `./gradlew :apps:commerce-api:test` and `./gradlew ktlintCheck` pass.

## File Structure

Create:

- `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRole.kt` - domain enum persisted on `User`.
- `apps/commerce-api/src/main/kotlin/com/loopers/support/auth/Admin.kt` - admin authorization marker.
- `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/AdminUserV1ApiSpec.kt` - OpenAPI contract for admin user creation.
- `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/AdminUserV1Controller.kt` - admin user creation endpoint.

Modify:

- `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/User.kt:1-52` - add immutable `role` field and string enum persistence.
- `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt:15-66` - route public/admin registration through the same rules with different roles.
- `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt:1-47` - expose `role` in `MyInfoResponse`; later add DTO-level request annotations.
- `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt:1-44` - later add `@Valid` on request bodies.
- `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/ApiControllerAdvice.kt:1-115` - handle method-argument validation failures as standard 400 envelopes.
- `apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt:5-12` - add `FORBIDDEN`.
- `apps/commerce-api/src/main/kotlin/com/loopers/support/auth/AuthenticationInterceptor.kt:1-41` - make `@Admin` imply login and enforce `UserRole.ADMIN`.
- `apps/commerce-api/build.gradle.kts:14-19` - wire the validation starter on the app compile classpath.

Test:

- `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserTest.kt`
- `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceTest.kt`
- `apps/commerce-api/src/test/kotlin/com/loopers/support/auth/AuthenticationInterceptorTest.kt`
- `apps/commerce-api/src/test/kotlin/com/loopers/support/auth/CurrentUserArgumentResolverTest.kt`
- `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/user/UserV1ApiE2ETest.kt`

---

### Task 1: Persist `UserRole` and Default Public Registration to `CONSUMER`

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRole.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/User.kt:1-52`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt:15-28`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceTest.kt`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/support/auth/CurrentUserArgumentResolverTest.kt`

- [ ] **Step 1: Write the failing domain tests**

Add this test inside `UserTest.Create`:

```kotlin
@DisplayName("role is stored from the constructor.")
@Test
fun storesRole() {
    val user = User(
        loginId = "loopers01",
        encryptedPassword = "hashed",
        name = "Alice",
        birthdate = LocalDate.of(1990, 1, 1),
        email = "user@example.com",
        role = UserRole.ADMIN,
    )

    assertThat(user.role).isEqualTo(UserRole.ADMIN)
}
```

Add this assertion to `UserServiceTest.Register.persistsAndReturnsUserWithGeneratedId`:

```kotlin
{ assertThat(saved.role).isEqualTo(UserRole.CONSUMER) },
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserTest" --tests "com.loopers.domain.user.UserServiceTest"
```

Expected: FAIL at Kotlin compilation with unresolved `UserRole` and/or no `role` constructor parameter on `User`.

- [ ] **Step 3: Add the minimal role implementation**

Create `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRole.kt`:

```kotlin
package com.loopers.domain.user

enum class UserRole {
    CONSUMER,
    ADMIN,
}
```

Modify `User.kt` imports:

```kotlin
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
```

Modify the `User` constructor to include `role` after `email`:

```kotlin
@Column(name = "email", nullable = false, length = 100)
val email: String,

@Enumerated(EnumType.STRING)
@Column(name = "role", nullable = false, length = 20)
val role: UserRole,
```

Modify the `UserService.register` user creation to pass `CONSUMER`:

```kotlin
val user = User(
    loginId = command.loginId,
    encryptedPassword = passwordEncoder.encode(command.rawPassword),
    name = command.name,
    birthdate = command.birthdate,
    email = command.email,
    role = UserRole.CONSUMER,
)
```

Update every direct `User(...)` construction in these tests with `role = UserRole.CONSUMER` unless the test is specifically asserting `UserRole.ADMIN`:

```text
apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserTest.kt
apps/commerce-api/src/test/kotlin/com/loopers/support/auth/CurrentUserArgumentResolverTest.kt
```

Example updated constructor:

```kotlin
val user = User(
    loginId = "loopers01",
    encryptedPassword = "hashed",
    name = "Alice",
    birthdate = LocalDate.of(1990, 1, 1),
    email = "user@example.com",
    role = UserRole.CONSUMER,
)
```

- [ ] **Step 4: Run the focused tests to verify they pass**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserTest" --tests "com.loopers.domain.user.UserServiceTest" --tests "com.loopers.support.auth.CurrentUserArgumentResolverTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserRole.kt apps/commerce-api/src/main/kotlin/com/loopers/domain/user/User.kt apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserTest.kt apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceTest.kt apps/commerce-api/src/test/kotlin/com/loopers/support/auth/CurrentUserArgumentResolverTest.kt
git commit -m "feat: add user roles"
```

---

### Task 2: Add Domain Service Admin Registration

**Files:**
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt:15-66`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceTest.kt`

- [ ] **Step 1: Write the failing service tests**

Add these tests inside `UserServiceTest.Register`:

```kotlin
@DisplayName("registerAdmin creates an ADMIN user.")
@Test
fun registerAdminCreatesAdminUser() {
    val saved = service.registerAdmin(
        UserCommand.Register(
            loginId = "admin01",
            rawPassword = RawPassword("abcd1234"),
            name = "Admin",
            birthdate = LocalDate.of(1988, 8, 8),
            email = "admin@example.com",
        ),
    )

    assertAll(
        { assertThat(saved.id).isNotZero() },
        { assertThat(saved.loginId).isEqualTo("admin01") },
        { assertThat(saved.role).isEqualTo(UserRole.ADMIN) },
    )
}

@DisplayName("registerAdmin reuses the same loginId uniqueness rule.")
@Test
fun registerAdminThrowsConflictWhenLoginIdAlreadyExists() {
    service.register(
        UserCommand.Register(
            loginId = "loopers01",
            rawPassword = RawPassword("abcd1234"),
            name = "Consumer",
            birthdate = LocalDate.of(1990, 1, 1),
            email = "user@example.com",
        ),
    )

    val ex = assertThrows<CoreException> {
        service.registerAdmin(
            UserCommand.Register(
                loginId = "loopers01",
                rawPassword = RawPassword("wxyz5678"),
                name = "Admin",
                birthdate = LocalDate.of(1988, 8, 8),
                email = "admin@example.com",
            ),
        )
    }

    assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserServiceTest"
```

Expected: FAIL at Kotlin compilation with `Unresolved reference: registerAdmin`.

- [ ] **Step 3: Route public and admin registration through one private method**

Replace the current public `register` method in `UserService.kt` with:

```kotlin
@Transactional
fun register(command: UserCommand.Register): User = register(command, UserRole.CONSUMER)

@Transactional
fun registerAdmin(command: UserCommand.Register): User = register(command, UserRole.ADMIN)

private fun register(command: UserCommand.Register, role: UserRole): User {
    if (userRepository.existsByLoginId(command.loginId)) {
        throw CoreException(ErrorType.CONFLICT, "Already used loginId.")
    }
    rejectIfPasswordContainsBirthdate(command.rawPassword, command.birthdate)
    val user = User(
        loginId = command.loginId,
        encryptedPassword = passwordEncoder.encode(command.rawPassword),
        name = command.name,
        birthdate = command.birthdate,
        email = command.email,
        role = role,
    )
    return userRepository.save(user)
}
```

Keep the existing `authenticate`, `changePassword`, and `rejectIfPasswordContainsBirthdate` methods unchanged.

- [ ] **Step 4: Run the focused service tests to verify they pass**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserServiceTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/domain/user/UserService.kt apps/commerce-api/src/test/kotlin/com/loopers/domain/user/UserServiceTest.kt
git commit -m "feat: add admin user registration service"
```

---

### Task 3: Enforce `@Admin` in the Authentication Interceptor

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/support/auth/Admin.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt:5-12`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/support/auth/AuthenticationInterceptor.kt:1-41`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/support/auth/AuthenticationInterceptorTest.kt`

- [ ] **Step 1: Write the failing interceptor tests**

Modify `AuthenticationInterceptorTest.TestController`:

```kotlin
@Suppress("unused")
class TestController {
    @LoginRequired
    fun protectedAction() = Unit

    @Admin
    fun adminAction() = Unit

    fun publicAction() = Unit
}

@Suppress("unused")
@Admin
class AdminClassController {
    fun adminClassAction() = Unit
}
```

Add this helper below `handlerMethodFor`:

```kotlin
private fun adminClassHandlerMethod(): HandlerMethod {
    val method = AdminClassController::class.java.getDeclaredMethod("adminClassAction")
    return HandlerMethod(AdminClassController(), method)
}
```

Add these helpers below `resp()`:

```kotlin
private fun registerConsumer() {
    userService.register(
        UserCommand.Register(
            loginId = "loopers01",
            rawPassword = RawPassword("abcd1234"),
            name = "Consumer",
            birthdate = LocalDate.of(1990, 1, 1),
            email = "user@example.com",
        ),
    )
}

private fun registerAdmin() {
    userService.registerAdmin(
        UserCommand.Register(
            loginId = "admin01",
            rawPassword = RawPassword("admin1234"),
            name = "Admin",
            birthdate = LocalDate.of(1988, 8, 8),
            email = "admin@example.com",
        ),
    )
}
```

Add these tests:

```kotlin
@DisplayName("@Admin with missing headers throws UNAUTHORIZED.")
@Test
fun throwsUnauthorizedWhenHeadersMissingOnAdminEndpoint() {
    val ex = assertThrows<CoreException> {
        interceptor.preHandle(req(), resp(), handlerMethodFor("adminAction"))
    }

    assertThat(ex.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
}

@DisplayName("@Admin with a CONSUMER user throws FORBIDDEN.")
@Test
fun throwsForbiddenWhenConsumerUsesAdminEndpoint() {
    registerConsumer()
    val request = req().apply {
        addHeader("X-Loopers-LoginId", "loopers01")
        addHeader("X-Loopers-LoginPw", "abcd1234")
    }

    val ex = assertThrows<CoreException> {
        interceptor.preHandle(request, resp(), handlerMethodFor("adminAction"))
    }

    assertThat(ex.errorType).isEqualTo(ErrorType.FORBIDDEN)
}

@DisplayName("@Admin with an ADMIN user authenticates and stashes the user.")
@Test
fun authenticatesAndStashesAdminOnAdminEndpoint() {
    registerAdmin()
    val request = req().apply {
        addHeader("X-Loopers-LoginId", "admin01")
        addHeader("X-Loopers-LoginPw", "admin1234")
    }

    val result = interceptor.preHandle(request, resp(), handlerMethodFor("adminAction"))

    assertThat(result).isTrue()
    val stashed = request.getAttribute(AuthenticationInterceptor.CURRENT_USER_KEY) as? com.loopers.domain.user.User
    assertThat(stashed).isNotNull()
    assertThat(stashed!!.role).isEqualTo(UserRole.ADMIN)
}

@DisplayName("@Admin on a class protects all handler methods in that class.")
@Test
fun supportsAdminClassAnnotation() {
    registerAdmin()
    val request = req().apply {
        addHeader("X-Loopers-LoginId", "admin01")
        addHeader("X-Loopers-LoginPw", "admin1234")
    }

    val result = interceptor.preHandle(request, resp(), adminClassHandlerMethod())

    assertThat(result).isTrue()
}
```

Add this import to `AuthenticationInterceptorTest.kt`:

```kotlin
import com.loopers.domain.user.UserRole
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.support.auth.AuthenticationInterceptorTest"
```

Expected: FAIL at Kotlin compilation with unresolved `Admin` and `ErrorType.FORBIDDEN`.

- [ ] **Step 3: Implement `@Admin`, 403, and interceptor authorization**

Create `apps/commerce-api/src/main/kotlin/com/loopers/support/auth/Admin.kt`:

```kotlin
package com.loopers.support.auth

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Admin
```

Modify `ErrorType.kt` by adding `FORBIDDEN` after `UNAUTHORIZED`:

```kotlin
FORBIDDEN(HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN.reasonPhrase, "Forbidden."),
```

Replace `AuthenticationInterceptor.kt` with:

```kotlin
package com.loopers.support.auth

import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRole
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuthenticationInterceptor(
    private val userService: UserService,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true

        val requiresAdmin = handler.hasAdmin()
        val requiresLogin = requiresAdmin || handler.hasLoginRequired()
        if (!requiresLogin) return true

        val loginId = request.getHeader(LOGIN_ID_HEADER)
        val loginPw = request.getHeader(LOGIN_PW_HEADER)
        if (loginId.isNullOrBlank() || loginPw.isNullOrBlank()) {
            throw CoreException(ErrorType.UNAUTHORIZED, "Authentication headers are required.")
        }

        val user = userService.authenticate(loginId, RawPassword(loginPw))
        request.setAttribute(CURRENT_USER_KEY, user)
        authorizeAdminIfRequired(requiresAdmin, user)
        return true
    }

    private fun HandlerMethod.hasLoginRequired(): Boolean =
        method.isAnnotationPresent(LoginRequired::class.java) ||
            beanType.isAnnotationPresent(LoginRequired::class.java)

    private fun HandlerMethod.hasAdmin(): Boolean =
        method.isAnnotationPresent(Admin::class.java) ||
            beanType.isAnnotationPresent(Admin::class.java)

    private fun authorizeAdminIfRequired(requiresAdmin: Boolean, user: User) {
        if (requiresAdmin && user.role != UserRole.ADMIN) {
            throw CoreException(ErrorType.FORBIDDEN, "Admin role is required.")
        }
    }

    companion object {
        const val LOGIN_ID_HEADER = "X-Loopers-LoginId"
        const val LOGIN_PW_HEADER = "X-Loopers-LoginPw"
        const val CURRENT_USER_KEY = "com.loopers.support.auth.CURRENT_USER"
    }
}
```

- [ ] **Step 4: Run the focused interceptor tests to verify they pass**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.support.auth.AuthenticationInterceptorTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/support/auth/Admin.kt apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt apps/commerce-api/src/main/kotlin/com/loopers/support/auth/AuthenticationInterceptor.kt apps/commerce-api/src/test/kotlin/com/loopers/support/auth/AuthenticationInterceptorTest.kt
git commit -m "feat: enforce admin authorization"
```

---

### Task 4: Add Admin User API and Include Role in `MyInfoResponse`

**Files:**
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/AdminUserV1ApiSpec.kt`
- Create: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/AdminUserV1Controller.kt`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt:1-47`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/user/UserV1ApiE2ETest.kt`

- [ ] **Step 1: Write the failing E2E tests**

Add these imports to `UserV1ApiE2ETest.kt`:

```kotlin
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRole
```

Add `passwordEncoder` to the test constructor:

```kotlin
private val passwordEncoder: PasswordEncoder,
```

Add this assertion to `SignUp.returnsMaskedMyInfo_whenValidSignUp`:

```kotlin
{ assertThat(response.body?.data?.role).isEqualTo(UserRole.CONSUMER) },
```

Add this nested E2E class to `UserV1ApiE2ETest`:

```kotlin
@DisplayName("POST /api/v1/admin/users")
@Nested
inner class AdminSignUp {
    private val infoType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>>() {}

    private fun authHeaders(loginId: String, password: String): HttpHeaders = HttpHeaders().apply {
        add("X-Loopers-LoginId", loginId)
        add("X-Loopers-LoginPw", password)
    }

    private fun saveAdmin() {
        userJpaRepository.save(
            User(
                loginId = "admin01",
                encryptedPassword = passwordEncoder.encode(RawPassword("admin1234")),
                name = "Admin",
                birthdate = LocalDate.of(1988, 8, 8),
                email = "admin@example.com",
                role = UserRole.ADMIN,
            ),
        )
    }

    private fun signUpConsumer() {
        val body = mapOf(
            "loginId" to "loopers01",
            "password" to "abcd1234",
            "name" to "Consumer",
            "birthdate" to "1990-01-01",
            "email" to "user@example.com",
        )
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, HttpEntity(body), infoType)
    }

    @DisplayName("an authenticated admin can create another ADMIN user.")
    @Test
    fun createsAdminUser_whenAuthenticatedAdmin() {
        saveAdmin()
        val body = mapOf(
            "loginId" to "newadmin",
            "password" to "wxyz5678",
            "name" to "Second Admin",
            "birthdate" to "1991-02-03",
            "email" to "newadmin@example.com",
        )

        val response = testRestTemplate.exchange(
            "/api/v1/admin/users",
            HttpMethod.POST,
            HttpEntity(body, authHeaders("admin01", "admin1234")),
            infoType,
        )

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.loginId).isEqualTo("newadmin") },
            { assertThat(response.body?.data?.role).isEqualTo(UserRole.ADMIN) },
            { assertThat(userJpaRepository.findByLoginId("newadmin")?.role).isEqualTo(UserRole.ADMIN) },
        )
    }

    @DisplayName("missing auth headers return 401.")
    @Test
    fun returnsUnauthorized_whenHeadersMissing() {
        val body = mapOf(
            "loginId" to "newadmin",
            "password" to "wxyz5678",
            "name" to "Second Admin",
            "birthdate" to "1991-02-03",
            "email" to "newadmin@example.com",
        )

        val response = testRestTemplate.exchange(
            "/api/v1/admin/users",
            HttpMethod.POST,
            HttpEntity(body),
            infoType,
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @DisplayName("authenticated consumers return 403.")
    @Test
    fun returnsForbidden_whenAuthenticatedConsumer() {
        signUpConsumer()
        val body = mapOf(
            "loginId" to "newadmin",
            "password" to "wxyz5678",
            "name" to "Second Admin",
            "birthdate" to "1991-02-03",
            "email" to "newadmin@example.com",
        )

        val response = testRestTemplate.exchange(
            "/api/v1/admin/users",
            HttpMethod.POST,
            HttpEntity(body, authHeaders("loopers01", "abcd1234")),
            infoType,
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }
}
```

- [ ] **Step 2: Run the E2E test to verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.user.UserV1ApiE2ETest"
```

Expected: FAIL with unresolved `MyInfoResponse.role` and/or 404 for `/api/v1/admin/users`.

- [ ] **Step 3: Implement response role and admin endpoint**

Modify `UserV1Dto.kt` imports:

```kotlin
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserRole
import java.time.LocalDate
```

Replace `MyInfoResponse` in `UserV1Dto.kt` with:

```kotlin
data class MyInfoResponse(
    val loginId: String,
    val name: String,
    val birthdate: LocalDate,
    val email: String,
    val role: UserRole,
) {
    companion object {
        fun from(user: User): MyInfoResponse = MyInfoResponse(
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

Create `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/AdminUserV1ApiSpec.kt`:

```kotlin
package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin User V1 API", description = "Loopers admin user API.")
interface AdminUserV1ApiSpec {
    @Operation(summary = "Admin sign-up", description = "Creates a new admin user.")
    fun signUpAdmin(request: UserV1Dto.SignUpRequest): ApiResponse<UserV1Dto.MyInfoResponse>
}
```

Create `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/AdminUserV1Controller.kt`:

```kotlin
package com.loopers.interfaces.api.user

import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.Admin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserV1Controller(
    private val userService: UserService,
) : AdminUserV1ApiSpec {
    @Admin
    @PostMapping
    override fun signUpAdmin(
        @RequestBody request: UserV1Dto.SignUpRequest,
    ): ApiResponse<UserV1Dto.MyInfoResponse> =
        userService.registerAdmin(request.toCommand())
            .let { UserV1Dto.MyInfoResponse.from(it) }
            .let { ApiResponse.success(it) }
}
```

- [ ] **Step 4: Run the E2E test to verify it passes**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.user.UserV1ApiE2ETest"
```

Expected: PASS. This command requires Docker because the test uses Testcontainers MySQL.

- [ ] **Step 5: Commit**

```bash
git add apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/AdminUserV1ApiSpec.kt apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/AdminUserV1Controller.kt apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/user/UserV1ApiE2ETest.kt
git commit -m "feat: add admin user API"
```

---

### Task 5: Wire Request Bean Validation and Standard 400 Handling

**Files:**
- Modify: `apps/commerce-api/build.gradle.kts:14-19`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt:1-52`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt:1-44`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/AdminUserV1Controller.kt:1-29`
- Modify: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/ApiControllerAdvice.kt:1-115`
- Test: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/user/UserV1ApiE2ETest.kt`

- [ ] **Step 1: Write the failing validation E2E test**

Add this test inside `UserV1ApiE2ETest.SignUp`:

```kotlin
@DisplayName("blank name returns a 400 field validation message.")
@Test
fun returnsBadRequestWithFieldMessage_whenNameBlank() {
    val body = mapOf(
        "loginId" to "loopers01",
        "password" to "abcd1234",
        "name" to "",
        "birthdate" to "1990-01-01",
        "email" to "user@example.com",
    )
    val responseType = object : ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>>() {}

    val response = testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, HttpEntity(body), responseType)

    assertAll(
        { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
        { assertThat(response.body?.meta?.message).contains("name") },
    )
}
```

- [ ] **Step 2: Run the E2E test to verify it fails**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.user.UserV1ApiE2ETest\$SignUp.returnsBadRequestWithFieldMessage_whenNameBlank"
```

Expected: FAIL because the current response is produced by entity validation and the error message does not contain the DTO field name `name`.

- [ ] **Step 3: Add compile dependency, DTO annotations, `@Valid`, and advice handling**

Modify `apps/commerce-api/build.gradle.kts` by adding the validation starter to the web dependency group:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-validation")
```

Modify `UserV1Dto.kt` imports:

```kotlin
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserRole
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
```

Replace `SignUpRequest` and `ChangePasswordRequest` in `UserV1Dto.kt` with:

```kotlin
data class SignUpRequest(
    @field:NotBlank
    val loginId: String,

    @field:NotBlank
    val password: String,

    @field:NotBlank
    val name: String,

    @field:NotNull
    val birthdate: LocalDate,

    @field:NotBlank
    @field:Email
    val email: String,
) {
    fun toCommand(): UserCommand.Register = UserCommand.Register(
        loginId = loginId,
        rawPassword = RawPassword(password),
        name = name,
        birthdate = birthdate,
        email = email,
    )
}

data class ChangePasswordRequest(
    @field:NotBlank
    val oldPassword: String,

    @field:NotBlank
    val newPassword: String,
)
```

Modify `UserV1Controller.kt` imports:

```kotlin
import jakarta.validation.Valid
```

Modify `UserV1Controller.signUp` request parameter:

```kotlin
@RequestBody @Valid request: UserV1Dto.SignUpRequest,
```

Modify `UserV1Controller.changePassword` request parameter:

```kotlin
@RequestBody @Valid request: UserV1Dto.ChangePasswordRequest,
```

Modify `AdminUserV1Controller.kt` imports:

```kotlin
import jakarta.validation.Valid
```

Modify `AdminUserV1Controller.signUpAdmin` request parameter:

```kotlin
@RequestBody @Valid request: UserV1Dto.SignUpRequest,
```

Modify `ApiControllerAdvice.kt` imports:

```kotlin
import org.springframework.web.bind.MethodArgumentNotValidException
```

Add this handler after `handle(e: CoreException)` in `ApiControllerAdvice.kt`:

```kotlin
@ExceptionHandler
fun handleBadRequest(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<*>> {
    val fieldError = e.bindingResult.fieldErrors.firstOrNull()
    val message = fieldError
        ?.let { "Field '${it.field}' is invalid: ${it.defaultMessage}" }
        ?: "Request body is invalid."
    return failureResponse(errorType = ErrorType.BAD_REQUEST, errorMessage = message)
}
```

- [ ] **Step 4: Run the focused E2E test to verify it passes**

Run:

```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.user.UserV1ApiE2ETest\$SignUp.returnsBadRequestWithFieldMessage_whenNameBlank"
```

Expected: PASS. This command requires Docker because the test uses Testcontainers MySQL.

- [ ] **Step 5: Run final verification**

Run:

```bash
grep -rE "@LoginRequired|@Admin" apps/commerce-api/src/main
```

Expected output includes:

```text
apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt:    @LoginRequired
apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt:    @LoginRequired
apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/AdminUserV1Controller.kt:    @Admin
```

Run:

```bash
./gradlew :apps:commerce-api:test
```

Expected: PASS.

Run:

```bash
./gradlew ktlintCheck
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/commerce-api/build.gradle.kts apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Dto.kt apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/UserV1Controller.kt apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/user/AdminUserV1Controller.kt apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/ApiControllerAdvice.kt apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/user/UserV1ApiE2ETest.kt
git commit -m "feat: validate user API requests"
```

---

## Self-Review

Spec coverage:

- Sign-up, get-my-info, and change-password remain in the existing controller and service. Task 1 preserves public sign-up while adding `CONSUMER`.
- Admin sign-up is covered by Task 2 domain service work and Task 4 controller/E2E work.
- Header-based auth and `@LoginRequired` remain in the current interceptor. Task 3 adds `@Admin` as an auth marker that implies login.
- Admin authorization is kept in `AuthenticationInterceptor` in Task 3 with `authorizeAdminIfRequired(...)`.
- `@CurrentUser` remains a thin resolver; this plan does not move authentication into it.
- Public endpoints keep ignoring headers because `AuthenticationInterceptor` still returns before reading headers when no auth marker exists.
- `FORBIDDEN` is added in Task 3 so bad credentials stay 401 and insufficient role becomes 403.
- `RawPassword`, bcrypt, birthdate-in-password, uniqueness, old-password recheck, and no-Spring-Security decisions already exist and are not refactored.
- `MyInfoResponse` role exposure is covered by Task 4.
- DTO shape checks and standard 400 response handling are covered by Task 5.
- No facade, no login/logout, no token issuance, no role mutation, and no bootstrap endpoint are introduced.

Placeholder scan:

- No blocked placeholder terms appear in task steps.
- Every code-changing step includes exact code blocks or exact file paths plus the specific constructor/request parameter shape to change.
- Every test step includes the command to run and the expected failure or pass signal.

Type consistency:

- `UserRole.CONSUMER` and `UserRole.ADMIN` are introduced in Task 1 and reused consistently in Tasks 2-4.
- `UserService.registerAdmin(command: UserCommand.Register): User` is introduced in Task 2 and used by `AdminUserV1Controller` in Task 4.
- `UserV1Dto.MyInfoResponse.role: UserRole` is introduced in Task 4 and asserted in E2E tests with the same enum type.
- `@Admin` is introduced in Task 3 and used by `AdminUserV1Controller` in Task 4.
