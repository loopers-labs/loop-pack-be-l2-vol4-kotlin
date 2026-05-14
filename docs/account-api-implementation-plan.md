# Account API Implementation Plan

이 문서는 `docs/account-api-architecture.md`를 기준으로 이번 주 Account API 구현 진행 상황을 기록하기 위한 작업 목록이다. 작업은 `assignment/week-01-member-account` 브랜치에서 진행하고, 최종 PR 대상은 `loopers-labs/loop-pack-be-l2-vol4-kotlin:shoeone96`로 둔다.

## Progress Board

| Area | Status | Notes |
| --- | --- | --- |
| Module setup | Not started | `account-api` 등록 및 의존성 정리 |
| Domain model | Not started | `Account`, `AccountCredential` 모델링 |
| Domain policy | Not started | 로그인 ID, 이름, 생년월일, 이메일, 비밀번호 정책 |
| Persistence | Not started | JPA repository, adapter, unique constraint |
| Application use cases | Not started | 회원가입, 내 정보 조회, 비밀번호 수정 |
| Security | Not started | Spring Security header authentication |
| API | Not started | Controller, DTO, response/error mapping |
| Tests | Not started | JUnit 기반 TDD 진행 |
| Verification | Not started | Gradle test, bootRun, PR 준비 |

## TDD Rule

- 구현 전 `$kent-beck-tdd` 기준으로 테스트 목록을 먼저 작성하고 사용자 컨펌을 받는다.
- 테스트 목록은 MECE하게 작성하되 과하게 쪼개지 않는다.
- 임계값 중심으로 테스트한다. 예: 비밀번호 길이 `7/8/16/17`, 생년월일 포함 여부, 중복 로그인 ID.
- 컨펌 후 Red-Green-Refactor 순서로 진행한다.
- 테스트 우선순위는 `domain policy -> domain model -> application -> API/security integration` 순서로 둔다.

## 1. Module Setup

- [ ] `settings.gradle.kts`에 `:apps:account-api` 포함 여부 확인 및 정리
- [ ] `apps/account-api/build.gradle.kts`를 `commerce-api` 패턴에 맞춰 정리
- [ ] `spring-boot-starter-security`는 `account-api`에만 추가
- [ ] 공통 의존성은 기존 구조를 따른다: `modules:jpa`, `supports:jackson`, `supports:logging`, `supports:monitoring`
- [ ] Querydsl/kapt 사용 여부는 기존 앱 패턴과 동일하게 유지
- [ ] `AccountApiApplication`과 기본 context loading 테스트 확인

## 2. Package Skeleton

- [ ] `interfaces/api/account`: HTTP controller, API spec, request/response DTO
- [ ] `interfaces/security`: security config, header auth filter, principal/token
- [ ] `application/account`: command, info, facade/use case service
- [ ] `domain/account`: model, enum, policy, domain service, repository port
- [ ] `infrastructure/account`: JPA repository, repository adapter, password encoder adapter
- [ ] `domain`이 Spring MVC, Security filter, DTO, JPA 구현체를 참조하지 않도록 확인

## 3. Domain Model

- [ ] `AccountModel` 정의
  - [ ] `name`
  - [ ] `birthDate`
  - [ ] `email`
  - [ ] base columns는 `BaseEntity` 관례를 따른다
- [ ] `AccountCredentialModel` 정의
  - [ ] `account`
  - [ ] `method`
  - [ ] `identifier`
  - [ ] `secret`
  - [ ] base columns는 `BaseEntity` 관례를 따른다
- [ ] `CredentialMethod.PASSWORD` 정의
- [ ] `method = PASSWORD`일 때 `identifier`는 로그인 ID로 사용
- [ ] `secret`에는 암호화된 비밀번호만 저장
- [ ] `account_credential(method, identifier)` 유니크 제약 추가
- [ ] 회원가입 시 `AccountModel`과 `AccountCredentialModel`을 함께 생성
- [ ] 비밀번호 수정은 `AccountCredentialModel.secret`만 변경

## 4. Domain Policy

- [ ] `LoginIdPolicy`
  - [ ] 영문과 숫자만 허용
  - [ ] 빈 값, 공백, 한글, 특수문자 거부
- [ ] `NamePolicy`
  - [ ] 이름 포맷 검증 기준 확정
  - [ ] 마지막 글자 `*` 마스킹
  - [ ] 한 글자 이름 처리 규칙 확정
- [ ] `BirthDatePolicy`
  - [ ] 입력 포맷 확정
  - [ ] 존재하지 않는 날짜 거부
  - [ ] 미래 날짜 허용 여부 확정
- [ ] `EmailPolicy`
  - [ ] 이메일 포맷 검증
  - [ ] 빈 값, 도메인 누락, 로컬 파트 누락 거부
- [ ] `PasswordPolicy`
  - [ ] 8~16자 허용
  - [ ] 영문 대/소문자, 숫자, 특수문자만 허용
  - [ ] 생년월일 문자열 포함 거부
  - [ ] 현재 비밀번호와 동일한 새 비밀번호 거부

## 5. Persistence

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

## 6. Application Use Cases

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

## 7. Spring Security

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

## 8. API Layer

- [ ] URI 확정
  - [ ] `POST /api/v1/accounts`
  - [ ] `GET /api/v1/accounts/me`
  - [ ] `PATCH /api/v1/accounts/me/password`
- [ ] request DTO 정의
- [ ] response DTO 정의
- [ ] application command/info 변환
- [ ] 기존 `ApiResponse` 패턴 적용
- [ ] 기존 `CoreException`, `ErrorType` 패턴에 맞춰 오류 응답 처리
- [ ] OpenAPI annotation은 기존 `commerce-api` 스타일을 따른다

## 9. Test Inventory

구현 전 아래 목록을 기준으로 실제 테스트 목록을 먼저 제안하고 컨펌받는다.

- [ ] `PasswordPolicyTest`
  - [ ] 길이 7 실패, 8 성공, 16 성공, 17 실패
  - [ ] 허용되지 않은 문자 실패
  - [ ] 생년월일 포함 실패
  - [ ] 현재 비밀번호 재사용 실패
- [ ] `LoginIdPolicyTest`
  - [ ] 영문/숫자 성공
  - [ ] 공백, 한글, 특수문자 실패
- [ ] `AccountModelTest`
  - [ ] 정상 account 생성
  - [ ] 이름 마스킹
- [ ] `AccountCredentialModelTest`
  - [ ] `PASSWORD` credential 생성
  - [ ] 비밀번호 변경
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

## 10. Verification

- [ ] `./gradlew :apps:account-api:test`
- [ ] `./gradlew test`
- [ ] `./gradlew :apps:account-api:bootRun`
- [ ] API 수동 확인
- [ ] `git status --short`로 의도하지 않은 변경 확인
- [ ] assignment branch push
- [ ] PR 설명에 구현 범위, 테스트 결과, 남은 이슈 기록

## Open Questions

- [ ] 이름 포맷을 어느 범위까지 허용할지 확정
- [ ] 생년월일 입력 포맷과 미래 날짜 정책 확정
- [ ] 인증 실패 시 기존 `ErrorType`을 재사용할지, `UNAUTHORIZED`를 추가할지 결정
- [ ] API URI를 위 제안대로 확정할지 결정
- [ ] 한 글자 이름 마스킹 결과를 `*`로 할지 빈 문자열 없이 유지할지 결정

## Change Log

| Date | Change | Notes |
| --- | --- | --- |
| 2026-05-14 | Initial implementation plan | Architecture decision 기반 TODO 작성 |
