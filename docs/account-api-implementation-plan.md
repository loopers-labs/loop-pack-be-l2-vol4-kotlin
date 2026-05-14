# Account API Implementation Plan

이 문서는 `docs/account-api-architecture.md`를 기준으로 이번 주 Account API 구현 진행 상황을 기록하기 위한 작업 목록이다. 작업은 `assignment/week-01-member-account` 브랜치에서 진행하고, 최종 PR 대상은 `loopers-labs/loop-pack-be-l2-vol4-kotlin:shoeone96`로 둔다.

## Progress Board

| Area | Status | Notes |
| --- | --- | --- |
| Module setup | Done | `account-api` 등록, JPA/logging/monitoring import, Security/Docker Compose 의존성 정리 |
| Common web support | Done | `supports:web` 응답 래핑, 공통 예외, ErrorCode, 테스트 fixture 구현 |
| Domain model | Done | `AccountModel`, `AccountCredentialModel`, Embeddable VO 구현 |
| Domain policy | In progress | 이메일, 이름, 생년월일, credential identifier/secret 완료. 비밀번호 정책 남음 |
| Persistence | Not started | JPA repository, adapter, unique constraint |
| Application use cases | Not started | 회원가입, 내 정보 조회, 비밀번호 수정 |
| Security | Not started | Spring Security header authentication 구현 남음 |
| API | Not started | Controller, DTO 구현 남음. 공통 response/error 기반은 완료 |
| Tests | In progress | domain/VO/support-web 단위 테스트 완료. use case/API/security 테스트 남음 |
| Verification | In progress | `:apps:account-api:build`, `:supports:web:build` 통과. bootRun/API 수동 확인 남음 |

## Current Snapshot

- 완료: 모듈 등록, 의존성 정리, Docker Compose MySQL, 공통 web 응답/예외 모듈, Account/AccountCredential 모델, Embeddable VO, domain/support-web 테스트.
- 진행 중: domain policy 중 비밀번호 정책 설계 및 테스트.
- 다음 순서: `PasswordPolicy` TDD -> repository port/JPA adapter -> 회원가입 use case -> header 인증 -> API controller.

## TDD Rule

- 구현 전 `$kent-beck-tdd` 기준으로 테스트 목록을 먼저 작성하고 사용자 컨펌을 받는다.
- 테스트 목록은 MECE하게 작성하되 과하게 쪼개지 않는다.
- 임계값 중심으로 테스트한다. 예: 비밀번호 길이 `7/8/16/17`, 생년월일 포함 여부, 중복 로그인 ID.
- 컨펌 후 Red-Green-Refactor 순서로 진행한다.
- 테스트 우선순위는 `domain policy -> domain model -> application -> API/security integration` 순서로 둔다.
- 반복 사용되는 테스트 객체는 `fixture`에 클래스/팩토리 형태로 둔다.

## 1. Module Setup

- [x] `settings.gradle.kts`에 `:apps:account-api` 포함 여부 확인 및 정리
- [x] `apps/account-api/build.gradle.kts`를 `commerce-api` 패턴에 맞춰 정리
- [x] `spring-boot-starter-security`는 `account-api`에만 추가
- [x] `spring-boot-docker-compose`는 `account-api`에만 `developmentOnly`로 추가
- [x] 루트 `compose.yaml`을 `jpa.yml` local MySQL 설정과 맞춰 추가
- [x] 공통 의존성은 기존 구조를 따른다: `modules:jpa`, `supports:jackson`, `supports:logging`, `supports:monitoring`, `supports:web`
- [x] `logging.yml`, `monitoring.yml` import 추가
- [x] `spring-boot-starter-actuator` 직접 의존성 제거. Actuator는 `supports:monitoring`을 통해 사용
- [x] Querydsl/kapt는 repository 구현 전까지 제거
- [x] `AccountApiApplication`과 기본 context loading 테스트 확인

## 2. Package Skeleton

- [ ] `interfaces/api/account`: HTTP controller, API spec, request/response DTO
- [ ] `interfaces/security`: security config, header auth filter, principal/token
- [ ] `application/account`: command, info, facade/use case service
- [x] `domain/account`: model, enum, VO
- [ ] `domain/account`: policy, domain service, repository port
- [ ] `infrastructure/account`: JPA repository, repository adapter, password encoder adapter
- [ ] `domain`이 Spring MVC, Security filter, DTO, JPA 구현체를 참조하지 않도록 확인

## 3. Common Web Support

- [x] `:supports:web` 모듈 등록
- [x] `ErrorCode` interface 정의
- [x] `CommonErrorCode` 정의
- [x] `CoreException`과 HTTP status별 예외 정의
- [x] `ApiResponse` 정의: `isSuccess`, `status`, `code`, `message`, `data`, `timestamp`
- [x] `ResponseBodyAdvice` 기반 성공 응답 wrapping 구현
- [x] `StringHttpMessageConverter` 응답 JSON 문자열 처리
- [x] 이미 `ApiResponse`인 응답과 actuator/swagger/api-docs/resource 응답 제외
- [x] `ControllerAdvice` 기반 예외 응답 변환
- [x] filter/security 실패 응답용 `ApiResponseWriter` 구현
- [x] support-web 테스트 fixture 구현
- [x] `:supports:web:build` 통과

## 4. Domain Model

- [x] `AccountModel` 정의
  - [x] `name`
  - [x] `birthDate`
  - [x] `email`
  - [x] base columns는 `BaseEntity` 관례를 따른다
- [x] `AccountCredentialModel` 정의
  - [x] `account`
  - [x] `method`
  - [x] `identifier`
  - [x] `secret`
  - [x] base columns는 `BaseEntity` 관례를 따른다
- [x] `CredentialMethod.PASSWORD` 정의
- [x] `method = PASSWORD`일 때 `identifier`는 로그인 ID로 사용
- [x] `secret`에는 암호화된 비밀번호만 저장
- [x] `account_credential(method, identifier)` 유니크 제약 추가
- [ ] 회원가입 시 `AccountModel`과 `AccountCredentialModel`을 함께 생성
- [x] 비밀번호 수정은 `AccountCredentialModel.secret`만 변경

## 5. Domain Policy

- [x] `CredentialIdentifier`
  - [x] `PASSWORD` 방식은 영문과 숫자만 허용
  - [x] 빈 값, 공백, 한글, 특수문자 거부
- [x] `AccountName`
  - [x] 이름은 blank 불가
  - [x] 마지막 글자 `*` 마스킹
  - [x] 한 글자 이름 처리 규칙 확정
- [x] `BirthDate`
  - [ ] 입력 포맷은 API DTO 단계에서 확정
  - [x] 존재하지 않는 날짜는 DTO 파싱 단계에서 거부 예정
  - [x] 미래 날짜 허용 여부 확정
- [x] `Email`
  - [x] 이메일 정규식 검증
  - [x] 빈 값, 도메인 누락, 로컬 파트 누락 거부
- [ ] `PasswordPolicy`
  - [ ] 8~16자 허용
  - [ ] 영문 대/소문자, 숫자, 특수문자만 허용
  - [ ] 생년월일 문자열 포함 거부
  - [ ] 현재 비밀번호와 동일한 새 비밀번호 거부

## 6. Persistence

- [ ] domain repository port 정의
  - [ ] `AccountRepository`
  - [ ] `AccountCredentialRepository`
- [ ] Spring Data JPA repository 정의
  - [ ] `AccountJpaRepository`
  - [ ] `AccountCredentialJpaRepository`
- [ ] repository adapter 구현
- [ ] `method + identifier` 조회 메서드 구현
- [ ] 로그인 ID 중복 여부 조회 구현
- [ ] 유니크 제약 위반을 도메인/애플리케이션 예외로 변환
- [ ] soft delete가 필요한 경우 기존 `BaseEntity` 관례에 맞춰 처리

## 7. Application Use Cases

- [ ] 회원가입
  - [ ] command 정의: 로그인 ID, 비밀번호, 이름, 생년월일, 이메일
  - [ ] 입력 정책 검증
  - [ ] 로그인 ID 중복 검사
  - [ ] 비밀번호 암호화
  - [ ] account와 credential 저장
- [ ] 내 정보 조회
  - [ ] 인증된 account 기준 조회
  - [ ] 반환 정보: 로그인 ID, 이름, 생년월일, 이메일
  - [ ] 이름 마지막 글자 마스킹
- [ ] 비밀번호 수정
  - [ ] 기존 비밀번호 검증
  - [ ] 새 비밀번호 정책 검증
  - [ ] 현재 비밀번호 재사용 거부
  - [ ] 새 비밀번호 암호화 후 저장
- [ ] 트랜잭션 경계는 application 계층에 둔다

## 8. Spring Security

- [ ] stateless security config 구성
- [ ] form login 비활성화
- [ ] default basic login 비활성화
- [ ] 회원가입 API는 `permitAll`
- [ ] 내 정보 조회, 비밀번호 수정 API는 인증 필요
- [ ] `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더 상수 정의
- [ ] custom authentication filter 구현
- [ ] 로그인 ID/비밀번호로 credential 조회 및 비밀번호 검증
- [ ] 인증 성공 시 account 식별자를 principal에 담는다
- [ ] 인증 실패 응답 정책 확정 및 구현
- [ ] domain에는 `PasswordEncryptor` port만 노출
- [ ] infrastructure에서 Spring Security `PasswordEncoder`로 구현

## 9. API Layer

- [ ] URI 확정
  - [ ] `POST /api/v1/accounts`
  - [ ] `GET /api/v1/accounts/me`
  - [ ] `PATCH /api/v1/accounts/me/password`
- [ ] request DTO 정의
- [ ] response DTO 정의
- [ ] application command/info 변환
- [x] `supports:web`의 `ApiResponse` 패턴 준비
- [x] `ErrorCode` interface와 domain별 error code enum 방식 준비
- [ ] API별 request/response mapping에서 오류 응답 확인
- [ ] OpenAPI annotation은 기존 `commerce-api` 스타일을 따른다

## 10. Test Inventory

구현 전 아래 목록을 기준으로 실제 테스트 목록을 먼저 제안하고 컨펌받는다.

- [ ] `PasswordPolicyTest`
  - [ ] 길이 7 실패, 8 성공, 16 성공, 17 실패
  - [ ] 허용되지 않은 문자 실패
  - [ ] 생년월일 포함 실패
  - [ ] 현재 비밀번호 재사용 실패
- [x] `CredentialIdentifierTest`
  - [x] 영문/숫자 성공
  - [x] 공백, 한글, 특수문자 실패
- [x] `AccountNameTest`
  - [x] 정상 이름 생성
  - [x] blank 실패
  - [x] 한 글자/두 글자 이상 마스킹
- [x] `BirthDateTest`
  - [x] 과거 날짜 성공
  - [x] 미래 날짜 실패
- [x] `EmailTest`
  - [x] 정상 이메일 성공
  - [x] 빈 값, 도메인 누락, 로컬 파트 누락 실패
- [x] `CredentialSecretTest`
  - [x] 암호화된 비밀번호 값 성공
  - [x] blank 실패
- [x] `AccountModelTest`
  - [x] 정상 account 생성
  - [x] 이름 마스킹
- [x] `AccountCredentialModelTest`
  - [x] `PASSWORD` credential 생성
  - [x] 비밀번호 변경
- [ ] `AccountSignUpUseCaseTest`
  - [ ] 회원가입 성공
  - [ ] 중복 로그인 ID 실패
  - [ ] 저장된 비밀번호가 평문이 아님
- [ ] `AccountMeUseCaseTest`
  - [ ] 내 정보 조회 성공
  - [ ] 이름 마스킹 반환
- [ ] `AccountPasswordChangeUseCaseTest`
  - [ ] 비밀번호 수정 성공
  - [ ] 기존 비밀번호 불일치 실패
  - [ ] 현재 비밀번호 재사용 실패
- [ ] `AccountApiE2ETest`
  - [ ] 회원가입은 인증 헤더 없이 성공
  - [ ] 보호 API는 헤더 없으면 실패
  - [ ] 보호 API는 올바른 헤더면 성공
  - [ ] 잘못된 비밀번호면 실패
- [x] `ApiResponseTest`
  - [x] 성공 응답 shape
  - [x] 실패 응답 shape
- [x] `ApiResponseBodyAdviceTest`
  - [x] 일반 객체 wrapping
  - [x] 이미 wrapped 응답 제외
  - [x] String 응답 JSON 처리
  - [x] actuator 응답 제외
- [x] `CoreExceptionTest`
  - [x] HTTP status와 ErrorCode 보존
  - [x] custom message 처리

## 11. Verification

- [x] `./gradlew :apps:account-api:test`
- [x] `./gradlew :apps:account-api:build`
- [x] `./gradlew :supports:web:test`
- [x] `./gradlew :supports:web:build`
- [ ] `./gradlew test`
- [ ] `./gradlew :apps:account-api:bootRun`
- [ ] API 수동 확인
- [ ] `git status --short`로 의도하지 않은 변경 확인
- [ ] assignment branch push
- [ ] PR 설명에 구현 범위, 테스트 결과, 남은 이슈 기록

## Open Questions

- [x] 이름 VO는 우선 blank만 거부한다
- [x] 생년월일은 미래 날짜를 거부한다
- [ ] 생년월일 API 입력 포맷 확정
- [x] `ErrorType` 대신 `ErrorCode` interface와 domain별 enum을 사용한다
- [ ] 인증 실패 세부 ErrorCode 결정
- [ ] API URI를 위 제안대로 확정할지 결정
- [x] 한 글자 이름 마스킹 결과는 `*`로 한다

## Change Log

| Date | Change | Notes |
| --- | --- | --- |
| 2026-05-14 | Initial implementation plan | Architecture decision 기반 TODO 작성 |
| 2026-05-14 | Domain model and VO tests | Embeddable VO, AccountModel, AccountCredentialModel 구현 |
| 2026-05-14 | Common web support and dependency cleanup | `supports:web`, response wrapping, ErrorCode, fixture, account-api 의존성 정리 |
