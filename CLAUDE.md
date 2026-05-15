# Claude Working Notes

## Account API Implementation Direction

- Implement from the current requirement outward. Start with the controller/API contract, then add only the next dependency that becomes necessary.
- Do not introduce `V1` naming or `/api/v1` routes unless explicitly requested. Use simple names such as `AccountController` and routes such as `/accounts`.
- Keep request DTOs in the controller file when they are only used by that controller. Split DTO files only when reuse or size makes it necessary.
- Do not create speculative `Facade`, security, or extra adapter layers before the test or requirement needs them.
- Use `Command` when crossing a real module boundary. `AccountCreateCommand` is the accepted API-to-application input model.
- When a layer becomes necessary, introduce it with a focused test and a clear responsibility boundary.

## Controller Test Strategy

- Prefer minimal controller tests first.
- Use `@WebMvcTest` when only controller mapping, JSON binding, and request/response shape are needed.
- Use `@SpringBootTest` + `@AutoConfigureMockMvc` when the full Spring context, `ControllerAdvice`, `ResponseBodyAdvice`, and security configuration need to participate without opening a real port.
- Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` only for true HTTP/E2E verification with an embedded server.
- For the current Account API thin slice, avoid `RANDOM_PORT` unless the test explicitly needs real HTTP behavior.

## Repository Test Strategy

- Service layer tests should mock domain repository ports and `PasswordEncryptor`.
- Repository behavior should be tested with `@DataJpaTest` using the embedded database by default.
- Do not import custom MySQL/Testcontainers configuration for repository tests unless MySQL-specific behavior must be verified.
- For `account-api`, use the H2 embedded test datasource in `src/test/resources/application-test.yaml`.
- Keep `modules:jpa` test fixtures off the account-api test classpath unless a test explicitly needs MySQL Testcontainers.
- Test account persistence behavior in `modules:account-persistence` with Spring Data JPA repositories and adapter wiring.
- Do not modify base template files such as `modules:jpa` `JpaConfig` for account-specific package preferences. Keep account JPA implementation in `modules:account-persistence` and connect it with account-specific persistence config.

## Module Boundaries

- `account-domain` owns entities, VO, validators, repository ports, and `PasswordEncryptor`.
- `account-application` owns use cases, commands, and transaction boundaries.
- `account-persistence` owns Spring Data JPA repositories and port adapters.
- `account-security` owns Spring Security crypto adapters.
- `supports:error` owns error codes and exceptions without Spring MVC status mapping.
- `supports:web` maps exceptions to HTTP responses and wraps successful bodies.

## API Response Wrapping

- Do not return `ApiResponse` directly from controllers.
- Controllers should return normal response bodies or no body; `ResponseBodyAdvice` wraps successful responses.
- `ApiResponse` should be used only by common web infrastructure such as exception handling, response advice, or filter/security failure writers.
- If a controller needs a response payload, return a domain-specific response DTO and let the advice wrap it.
- If a controller has no payload yet, keep the method body-focused and avoid adding placeholder response DTOs just to satisfy structure.
- Follow the repository's shared Jackson `NON_NULL` policy: nullable response fields such as `data` may be omitted when null.

## TDD Flow Reminder

- Write the failing test first.
- Add the smallest production code that makes it pass.
- Refactor only after green.
- Keep the test list MECE and boundary-focused, but do not add speculative tests for behavior that is not needed yet.
