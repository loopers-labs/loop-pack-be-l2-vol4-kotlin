# Account API Architecture

## Decisions

- 회원 기능은 `apps:account-api`라는 별도 실행 애플리케이션에서 구현한다.
- `account-domain`, `account-security` 같은 추가 Gradle 모듈은 아직 만들지 않는다.
- `account-api` 내부는 Layered Architecture 패키지로 나누고, 추후 모듈 분리가 쉬운 경계를 유지한다.
- 엔티티와 도메인 모델은 현재 코드베이스 관례에 맞춰 하나로 사용한다.
- Spring Security는 `account-api`에만 적용한다.

## Module And Package Structure

```text
apps/account-api
└── src/main/kotlin/com/loopers
    ├── interfaces/api/account
    ├── interfaces/security
    ├── application/account
    ├── domain/account
    └── infrastructure/account
```

- `interfaces/api/account`: Controller, API spec, request/response DTO, header 입력 처리
- `interfaces/security`: `SecurityFilterChain`, custom authentication filter, 인증 토큰/실패 처리
- `application/account`: 회원가입, 내 정보 조회, 비밀번호 수정 유스케이스와 트랜잭션 경계
- `domain/account`: `AccountModel`, `AccountCredentialModel`, 도메인 정책, repository/encryptor port
- `infrastructure/account`: Spring Data JPA 구현체, Spring Security `PasswordEncoder` 어댑터

의존 방향은 아래처럼 유지한다.

```text
interfaces -> application -> domain
infrastructure -> domain
```

`domain`은 HTTP DTO, `ApiResponse`, Spring MVC, Spring Security filter, JPA repository 구현을 알지 않는다.

## Domain Model

회원 프로필과 인증 수단은 분리한다. `AccountModel`은 사용자 프로필과 식별 대상이고, `AccountCredentialModel`은 로그인 ID/비밀번호 같은 인증 수단이다. 민감 정보와 인증 방식은 변경 축이 다르고, 한 account가 여러 credential을 가질 수 있으므로 같은 엔티티에 묶지 않는다.

```text
account
├── id
├── name
├── birth_date
├── email
└── base columns

account_credential
├── id
├── account_id
├── method
├── identifier
├── secret
└── base columns
```

초기 모델링 규칙:

- `method = PASSWORD`일 때 `identifier`는 사용자가 입력한 로그인 ID이다.
- `secret`에는 암호화된 비밀번호만 저장한다.
- `account`는 비밀번호를 알지 않는다.
- 유니크 제약은 `account_credential(method, identifier)` 기준으로 둔다.
- 회원가입은 `AccountModel`과 `AccountCredentialModel`을 함께 생성한다.
- 비밀번호 수정은 account profile 변경이 아니라 credential 변경이다.

OAuth 등이 실제로 필요해지면 `method`에 `GOOGLE`, `KAKAO` 같은 값을 추가하고, `identifier`에는 provider subject를 저장한다. 지금은 `PASSWORD`만 구현한다.

## Spring Security

`account-api`에는 `spring-boot-starter-security`를 추가한다. 버전은 루트 Gradle의 Spring Boot dependency management를 따르므로 별도 버전을 명시하지 않는다.

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
}
```

이 의존성은 루트 `subprojects` 공통 의존성에 넣지 않는다. Spring Security가 classpath에 있으면 보안 자동 설정이 활성화될 수 있으므로, 사용 앱인 `account-api`에만 둔다.

기본 전략:

- 회원가입 API는 `permitAll`
- 내 정보 조회, 비밀번호 수정은 인증 필요
- `X-Loopers-LoginId`, `X-Loopers-LoginPw`를 읽는 custom filter 사용
- stateless API로 구성하고 form login/default basic login은 비활성화
- 비밀번호 저장/검증은 Spring Security `PasswordEncoder` 사용
- domain에는 `PasswordEncryptor` port를 두고, infrastructure가 `PasswordEncoder`로 구현한다.

## Future Extraction

아래 조건이 실제로 생기면 별도 모듈 분리를 검토한다.

- 여러 앱이 account 도메인 규칙을 직접 재사용한다.
- `account-batch`, `account-streamer`, `admin-api` 같은 실행 앱이 추가된다.
- 동일한 인증 필터/클라이언트 코드가 여러 앱에서 필요해진다.
- account core를 독립 빌드/테스트할 만큼 도메인이 커진다.

`commerce-api`가 나중에 계정 인증이 필요하더라도 `AccountModel`이나 repository를 직접 의존하지 않는다. 우선 `account-api` 호출 또는 `account-client` 모듈을 검토한다.

## References

- [우아한형제들: 멀티모듈 설계 이야기](https://techblog.woowahan.com/2637/)
- [Spring Boot: Spring Security](https://docs.spring.io/spring-boot/reference/web/spring-security.html)
- [Spring Security: Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Spring Security: Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
