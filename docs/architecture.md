# Low level 설계 문서

이 문서는 시스템 전반에 적용되는 낮은 수준의 아키텍처 원칙을 정의한다.
각 bounded context의 세부 도메인 모델링, 컬럼 타입, API 상세 규약은 이 문서에서 다루지 않는다.

## 1. 시스템 범위와 제약

현재 시스템은 모놀리식 구조를 기본으로 한다.
Redis, Kafka 등 분산 인프라를 전제로 한 설계는 현재 문서 범위에서 다루지 않는다.

현재 단계에서는 JPA의 `ddl-auto: create`를 사용한다.

## 2. 레이어드 아키텍처

레이어드 아키텍처를 사용한다.

- `controller`: HTTP 요청과 응답을 담당한다.
- `application`: use case 실행을 담당한다. DB transaction, 외부 인프라 호출, 도메인 서비스와 도메인 모델의 연결을 제어한다.
- `domain`: 도메인 엔터티, 값 객체, 도메인 서비스, repository, DAO interface를 담당한다.
- `infrastructure`: DB query, DAO 구현, 외부 인프라 연동을 담당한다.

의존 방향은 다음을 따른다.

```text
controller -> application -> domain <- infrastructure
```

`controller`는 `domain service`를 직접 호출하지 않는다.

`application` 레이어는 두 종류로 나눈다.

- `ApplicationService`: 하나의 bounded context 안에서 여러 도메인 객체에 걸친 use case를 실행한다.
- `Facade`: bounded context 경계를 넘는 use case를 조율한다.

간단한 CRUD에서는 `ApplicationService`가 도메인 서비스를 거치지 않고 도메인 엔터티와 repository를 직접 사용해도 된다.
DB transaction은 항상 `application` 레이어의 public method에 둔다.

`controller` request DTO는 `controller` 레이어에 둔다.
response는 `application` 레이어의 `Info` 또는 read model을 그대로 반환할 수 있다.
단, domain entity를 API response로 직접 반환하지 않는다.
API별 masking이나 formatting이 필요하면 controller 전용 response DTO를 둔다.

## 3. Bounded Context

bounded context는 조회와 집계 요구가 가능한 한 하나의 context 안에서 해결되도록 잡는다.
cross-context 조회나 집계가 자주 필요하다면 bounded context가 잘못 나뉜 것은 아닌지 먼저 검토한다.

같은 bounded context 내부에서는 필요한 경우 엔터티 간 연관을 허용한다.
bounded context 경계를 넘는 참조는 다른 context의 엔터티를 직접 참조하지 않고 식별자만 저장한다.

bounded context 경계를 넘는 use case가 필요한 경우 `Facade` 또는 `application` 레이어에서 요청한다.

## 4. CQRS

CQRS를 엄격한 프레임워크가 아니라 레이어 운용 원칙으로 적용한다.

도메인 상태를 변경하는 command는 `application` 레이어에서 transaction을 열고 domain entity와 domain service를 적극적으로 사용한다.

조회와 집계는 `infrastructure` 레이어의 조회 전용 query에서 처리한다.
조회 결과는 domain entity가 아니라 `application` 레이어의 read model 또는 `Info`로 반환한다.

## 5. JPA 사용 원칙

도메인 엔터티가 JPA mapping annotation을 가지는 것은 허용한다.
`@Entity`, `@Table`, `@Column`, `@Id`, `@GeneratedValue`, `@Enumerated`, `@Embedded` 같은 mapping 목적의 annotation을 사용할 수 있다.

도메인 레이어의 repository 인터페이스가 JpaRepository를 상속하는 것 또한 허용한다.

단, QueryDsl을 통해 여러 엔터티에 걸쳐 조인이나 집계를 하는 함수는 DAO

조회 최적화를 위한 JPA 기능은 허용한다.
예를 들어 lazy association이나 batch fetch는 사용할 수 있다.

영속성 동작을 숨기는 JPA 기능은 사용하지 않는다.
JPA Auditing, entity lifecycle callback, cascade, orphanRemoval, entity graph는 사용하지 않는다.

`BaseEntity`는 lifecycle callback을 사용하지 않는다.
대신 `createMeta()`와 `updateMeta()` 같은 명시적 메타데이터 갱신 메서드를 제공한다.
repository 구현체는 저장 시점에 이 메서드를 명시적으로 호출한다.

soft delete 누락 위험을 줄이기 위해 `@SQLRestriction("deleted_at IS NULL")`과 `@SQLDelete`는 예외적으로 허용한다.
delete는 `@SQLDelete`를 사용하는 방식으로 일관성을 맞춘다.
JDBC나 batch에서 같은 테이블을 다룰 경우 JPA annotation이 적용되지 않으므로, query에 `deleted_at IS NULL` 조건과 soft delete update SQL을 명시해야 한다.
이 주의사항은 해당 엔터티나 repository 코드에도 주석으로 남긴다.

양방향 연관관계는 최대한 지양한다.
특히 `OneToOne` 연관관계는 엔터티의 생명주기가 같지 않다면 사용하지 않는다.

모든 enum은 `@Enumerated(EnumType.STRING)`과 `VARCHAR` 저장을 기본으로 한다.

## 6. DB 설계 원칙

PK는 `auto_increment`를 사용한다.

물리 FK는 사용하지 않는다.
논리적으로는 참조 관계를 둘 수 있지만, DB constraint로 foreign key를 생성하지 않는다.
FK lock으로 인한 deadlock과 lock contention 가능성을 줄이기 위한 결정이다.

unique constraint는 명시한다.
unique constraint는 동시성 제어의 최후 방어선이며, 기본 동시성 제어는 application 레이어에서 수행한다.

기본 동시성 전략은 optimistic lock과 조건부 update다.
lost update는 `update ... set ... where ...` 형태의 조건부 update로 방지한다.
충돌이 발생하면 같은 transaction 안에서 다시 조회하지 않고, 새 transaction에서 상태를 다시 읽어 use case를 재시도한다.
정해진 횟수만큼 재시도한 뒤에도 충돌하면 `409 CONFLICT`를 반환한다.

기본 transaction isolation은 `REPEATABLE READ`로 둔다.

soft delete를 기본 삭제 정책으로 둔다.
hard delete는 특수한 상황에서만 사용한다.
soft delete된 row의 business key는 재사용하지 않는다.
따라서 unique constraint는 기본적으로 `deleted_at`과 무관하게 business key 자체에 건다.

시간 타입은 한국 내 서비스만을 고려하여 `LocalDateTime`을 사용한다.
