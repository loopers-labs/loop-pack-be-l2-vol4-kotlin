# Soft Delete: `deletedAt` 타임스탬프 vs `status` enum 상태머신

> 환경: Spring Boot 3.4.4 (→ Hibernate ORM 6.6), Kotlin 2.0.20, Java 21
> 대상 결정: `Brand`를 `deletedAt IS NULL` 판단이 아니라 `BrandStatus`(ACTIVE/DELETED) enum 상태머신으로 모델링
>
> 본 문서의 모든 사실 주장에는 출처 각주를 답니다. 직접 WebFetch로 원문을 확인하지 못한 항목은 "(출처 미확인)"으로 표기합니다.

---

## ① 한 줄 결론 + 의사결정 표

**결론**: soft delete를 timestamp(`deletedAt`)로 표현하는 것은 anti-pattern이 아니며, 세 가지 모델(boolean / enum / timestamp) 모두 유효합니다.[^thorben] 다만 **엔티티가 다중 lifecycle 상태로 확장될 전제**가 있고 NULL을 도메인 상태로 오버로딩하는 것을 피하려는 경우 enum 상태머신이 더 적합하며, 이것이 우리가 `Brand`에 대해 내린 결정입니다.

| 상황 | 권장 모델 | 근거 |
|---|---|---|
| 2상태(살아있음/삭제됨)만 영원히 유지 | boolean flag 또는 timestamp | 가장 단순. 상태가 2개뿐이면 enum의 self-documenting 이점이 크지 않음 |
| 다중 상태 + 전이 규칙(어떤 상태→어떤 상태만 허용)이 도메인 invariant | **status enum 상태머신** | 유효 전이를 명시적으로 표현(State Machine 패턴)[^fowler] |
| "언제 삭제됐나" audit이 필요 | timestamp(`deletedAt`) 보존 | timestamp는 삭제 시점을 자체적으로 기록[^thorben] |
| 모든 쿼리에서 삭제 행을 자동 제외하고 싶음 (Hibernate에 위임) | `@SoftDelete`(6.4+, boolean) 또는 `@SQLRestriction` | Hibernate가 SQL을 자동 생성/조정[^thorben][^sqlr-since] |
| 위 두 가지(다중 상태 + audit)를 동시에 | **status enum + 별도 `deletedAt` audit 컬럼** | 상태는 enum이 표현, 삭제 시점은 timestamp가 별도로 보존 (우리 결정) |

---

## ② Soft delete의 3가지 모델

Thorben Janssen은 soft delete indicator를 세 가지로 정리합니다(verbatim):

> "a boolean that indicates if the record is active or deleted, an enumerated which models the state of the record, a timestamp that stores the date and time when the soft delete was performed."[^thorben]

- **boolean flag** — `deleted: Boolean`. 가장 단순. "살아있음/삭제됨" 2상태만 표현.
- **enum (상태값)** — 레코드의 상태를 모델링. 다중 상태 표현 가능.
- **timestamp** — 삭제가 수행된 일시를 저장. 삭제 여부(`null` 판단)와 삭제 시점(audit)을 한 컬럼이 겸함.

세 모델 모두 유효하며, timestamp 방식이 anti-pattern으로 규정되는 서술은 확인되지 않았습니다. 선택 기준은 위 ①의 표대로 "상태 수"와 "audit 필요 여부"입니다.

> 우리 프로젝트의 `BaseEntity`는 이미 `deletedAt: ZonedDateTime?` + 멱등 `delete()`/`restore()`를 빌트인으로 제공합니다 (`modules/persistence-core/src/main/kotlin/com/loopers/domain/BaseEntity.kt`). 즉 플랜의 기본값은 **timestamp 모델**입니다.

---

## ③ Hibernate `@SoftDelete` · `@SQLRestriction`/`@Where` 자동 필터 (버전 주의)

### `@SoftDelete` (Hibernate 6.4+)

Hibernate는 6.4에서 공식 soft delete 기능 `@SoftDelete`를 도입했습니다.[^thorben] 엔티티에 어노테이션만 붙이면:

> "generates the required SQL UPDATE statement for soft deleting a record and adjusts all query statements to exclude soft deleted records."[^thorben]

즉 삭제는 `DELETE` 대신 `UPDATE`로 처리되고, **모든 쿼리에서 삭제된 행이 자동 제외**됩니다.

**`@SoftDelete` 어노테이션 속성 (Hibernate 6.6 javadoc verbatim):**

- 클래스 설명: "Describes a soft-delete indicator mapping. Soft deletes handle 'deletions' from a database table by setting a column in the table to indicate deletion."[^sd-javadoc]
- `strategy` — "The strategy to use for storing/reading values to/from the database. The strategy also affects the default column name." 기본값: `SoftDeleteType.DELETED`[^sd-javadoc]
- `columnName` — "(Optional) The name of the column. Default depends on the strategy being used." 기본값: `""`[^sd-javadoc]
- `converter` — domain 표현이 `true`(deleted) / `false`(NOT deleted)인 boolean 값을 DB에 저장. "By default, values are stored as booleans in the database according to the dialect and settings."[^sd-javadoc]

### ⚠️ 버전 핵심: 우리 환경(6.6)의 `SoftDeleteType`은 boolean 전략만 지원, TIMESTAMP 없음

`SoftDeleteType`은 버전에 따라 다릅니다 — 이 점을 정확히 구분해야 합니다.

**Hibernate 6.6 (= Spring Boot 3.4.4가 끌어오는 버전)의 `SoftDeleteType` enum 상수 — 직접 javadoc 확인 결과 2개뿐:**[^sdt-66]

| 상수 | 설명 (verbatim) |
|---|---|
| `ACTIVE` | "Tracks rows which are active. ... `true` indicates that the row is active (non-deleted) `false` indicates that the row is inactive (deleted)" |
| `DELETED` | "Tracks rows which are deleted. ... `true` indicates that the row is deleted `false` indicates that the row is non-deleted" |

→ **6.6에는 `TIMESTAMP` 상수가 존재하지 않습니다.** `@SoftDelete`의 `converter` 속성 설명도 6.6에서는 boolean 변환만 언급하며, TIMESTAMP/`UnsupportedMappingException`에 대한 언급이 없습니다(직접 확인).[^sd-javadoc][^sdt-66]

**Hibernate 7.1의 `SoftDeleteType`은 3개 — TIMESTAMP 포함:**[^sdt-71]

| 상수 | 설명 (verbatim) |
|---|---|
| `TIMESTAMP` | "Tracks rows which are deleted by the timestamp at which they were deleted. `null` indicates that the row is non-deleted non-`null` indicates that the row is deleted, at the given timestamp" |

> **검증 노트 (1차 검색 요약기 과일반화 경계):** 일부 검색 요약은 "TIMESTAMP가 6.6에서도 지원된다(UnsupportedMappingException 언급 근거)"고 주장했습니다. 그러나 6.6 javadoc 원문을 두 번 직접 fetch해 확인한 결과 **6.6의 `SoftDeleteType`은 ACTIVE/DELETED 2개뿐**이고 TIMESTAMP는 없습니다. TIMESTAMP는 7.x 계열(직접 확인은 7.1)에서 확인됩니다. 따라서 **"`@SoftDelete`가 boolean/TIMESTAMP 전략을 지원한다"는 주장은 우리 환경(6.6)에서는 boolean(ACTIVE/DELETED)만 참**입니다. 이 결론은 본 환경에서 빌트인 `deletedAt` timestamp 모델을 Hibernate `@SoftDelete`로 그대로 옮기기 어렵다는 실무적 함의를 가집니다 — 6.6 `@SoftDelete`는 boolean 컬럼을 전제하기 때문입니다.

### `@Where` → `@SQLRestriction` (Hibernate 6.3에서 deprecated)

엔티티/컬렉션 전역에 native SQL 제약을 자동 적용하는 어노테이션입니다.

- `@Where`는 deprecated. javadoc verbatim: "Deprecated. Use `SQLRestriction`"[^where-javadoc]
- `@SQLRestriction` javadoc: "Specifies a restriction written in native SQL to add to the generated SQL for entities or collections." `Since: 6.3`[^sqlr-66][^sqlr-since]

→ 즉 대체는 **Hibernate 6.3** 시점입니다(`@SQLRestriction`의 `@since 6.3`로 확인). soft delete에 응용하면 `@SQLRestriction("deleted_at is null")` 또는 `@SQLRestriction("status <> 'DELETED'")`처럼 엔티티 전역 필터를 자동 적용할 수 있습니다.

### `@SQLRestriction` vs `@Filter` — static vs dynamic

`@SQLRestriction` javadoc verbatim:

> "@SQLRestrictions are always applied and cannot be disabled. Nor may they be parameterized. They're therefore _much_ less flexible than filters."[^sqlr-66]

| | `@SQLRestriction` | `@Filter` |
|---|---|---|
| 적용 | 항상 자동 적용, 비활성화 불가 | 동적 — 세션에서 수동 enable |
| 파라미터 | 불가 | 가능 |
| 용도 | soft delete처럼 "항상 켜져 있어야 하는" 전역 필터 | 멀티테넌시 등 런타임 조건 |

---

## ④ `deletedAt`(null 판단)의 한계 vs `status` 상태머신의 장점

### SQL NULL은 "unknown"이라 도메인 상태로 오버로딩하기 부적합

SQL `NULL`은 값의 부재/미지를 의미하며, 비교 시 3치 논리(three-valued logic)를 따릅니다. PostgreSQL 문서 verbatim:

> "Ordinary comparison operators yield null (signifying 'unknown'), not true or false, when either input is null. For example, `7 = NULL` yields null, as does `7 <> NULL`."[^pg-null]
>
> "The null value represents an unknown value, and it is not known whether two unknown values are equal."[^pg-null]
>
> "Do _not_ write `expression = NULL` because `NULL` is not 'equal to' `NULL`."[^pg-null]

함의:

- `deletedAt IS NULL`은 **2상태(삭제 안 됨 / 삭제됨)만** 표현할 수 있습니다. 세 번째 상태(예: `SUSPENDED`, `ARCHIVED`)가 생기면 NULL 하나로는 표현 불가 — 추가 컬럼이 필요해집니다.
- NULL 비교는 항상 `IS NULL` / `IS NOT NULL`(또는 `IS DISTINCT FROM`)을 강제하며, 일반 비교(`=`/`<>`)는 unknown을 낳아 버그 표면이 됩니다.[^pg-null] 도메인 상태를 NULL에 인코딩하면 이 함정을 쿼리마다 떠안게 됩니다.

### status 상태머신의 장점

Martin Fowler의 State Machine 패턴 verbatim:

> "Model a system as a set of explicit states with transitions between them."[^fowler]
>
> 패턴은 "classify these different internal states and describe both the differences in response and what causes the system to move between these states"를 돕습니다.[^fowler]

- **self-documenting**: `BrandStatus.ACTIVE`/`DELETED`는 enum 이름 자체가 상태를 설명합니다. `deletedAt != null`보다 의도가 코드/스키마에 드러납니다.
- **전이 invariant 표현**: `transitionTo(target)`에서 허용되지 않은 전이를 예외로 막을 수 있습니다 — "어떤 상태에서 어떤 상태로만 갈 수 있다"는 도메인 규칙을 타입/메서드로 강제. NULL 판단 모델에는 이 표현력이 없습니다.
- **확장성**: 상태 추가가 enum 상수 추가 + 전이 규칙 추가로 끝납니다. NULL-as-state는 상태가 늘면 곧바로 표현력이 무너집니다.
- **쿼리**: 조회는 `status <> 'DELETED'`로 일반 비교가 가능 — NULL 3치 논리 함정을 피합니다.

---

## ⑤ 우리 결정과 이유

**결정**: `Brand`를 `deletedAt IS NULL` 판단이 아니라 `BrandStatus`(ACTIVE/DELETED) enum 상태머신으로 모델링합니다.

- 조회: `status <> DELETED` (NULL 3치 논리 회피, 일반 비교)
- `deletedAt`: 삭제 여부 판단이 아니라 **audit(삭제 시점) 보존** 용도로만 유지. timestamp 모델의 "언제 삭제됐나" 강점[^thorben]을 audit로 살림.
- 전이: `transitionTo(target)`로 처리 — 멱등(같은 상태로의 전이는 무해), 허용되지 않은 전이는 예외(State Machine invariant).[^fowler]

**근거 3가지**

1. **상태 증가 전제**: `Brand`가 향후 다중 lifecycle 상태(예: 비활성/보관 등)로 확장될 전제가 있습니다. ①의 표대로 "다중 상태 + 전이 규칙"은 enum 상태머신 영역입니다.[^fowler] 2상태 고정이라면 boolean/timestamp로 충분했겠으나, 확장 전제가 enum을 정당화합니다.
2. **NULL-as-state 회피**: SQL NULL은 unknown 의미라 도메인 상태 인코딩에 부적합하고, 다중 상태로 확장 불가하며, 쿼리마다 3치 논리 함정을 떠안깁니다.[^pg-null]
3. **deletedAt를 audit로 분리**: 상태 판단 책임은 `status`가, "언제 삭제됐나"는 `deletedAt`가 각자 맡습니다. 한 컬럼(NULL 여부)이 "삭제 여부 판단 + 시점 기록"을 겸하던 것을, 책임을 분리해 의미 정합성을 높입니다.

**플랜의 deletedAt-only에서 이탈한 트레이드오프 (도메인 일관성)**

- `BaseEntity`는 `deletedAt` + 멱등 `delete()`/`restore()`를 빌트인으로 제공합니다. 플랜의 경로를 그대로 따랐다면 추가 코드 없이 `delete()`만 호출하면 됐습니다.
- 이탈 비용: `BrandStatus` enum + `transitionTo` 전이 로직 + 조회 조건(`status <> DELETED`)을 직접 작성해야 하고, `BaseEntity.delete()`의 빌트인 멱등성과 별개로 상태 전이 멱등성/유효성을 우리가 책임집니다. 즉 **공통 인프라가 주는 무료 동작을 일부 포기**하는 비용입니다.
- 이탈 이득: **도메인 일관성** — 상태가 self-documenting하고, 유효 전이가 invariant로 강제되며, 다중 상태 확장이 자연스럽고, NULL 3치 논리 함정을 회피합니다.
- 추가 주의: 6.6 `@SoftDelete`는 boolean 전략만 지원하므로(③ 참조), enum 상태 모델을 Hibernate의 `@SoftDelete` 자동 필터에 그대로 위임할 수 없습니다. 전역 자동 필터가 필요하면 `@SQLRestriction("status <> 'DELETED'")`가 6.6에서 사용 가능한 경로입니다.[^sqlr-66][^sqlr-since]
- YAGNI 점검: "상태가 늘어날 전제"가 실제 요구사항으로 뒷받침되지 않는다면, 빌트인 `deletedAt`(timestamp 모델)로 충분하다는 반론이 성립합니다. 이 이탈은 그 전제가 참일 때만 정당화됩니다.

---

## ⑥ 출처 목록

[^thorben]: Thorben Janssen, "How to implement a soft delete with Hibernate". soft delete indicator 3가지(boolean/enumerated/timestamp), `@SoftDelete`(6.4+)가 SQL UPDATE 생성 + 모든 쿼리에서 삭제 행 자동 제외. WebFetch 직접 확인. https://thorben-janssen.com/implement-soft-delete-hibernate/

[^sd-javadoc]: Hibernate ORM 6.6 Javadoc — `@SoftDelete` 어노테이션. 클래스 설명, `strategy`(기본 `SoftDeleteType.DELETED`), `columnName`(기본 `""`), `converter`(boolean 변환, 기본 dialect/settings). WebFetch 직접 확인 — 6.6에서 TIMESTAMP/UnsupportedMappingException 언급 없음. https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/annotations/SoftDelete.html

[^sdt-66]: Hibernate ORM 6.6 Javadoc — `SoftDeleteType` enum. 상수 ACTIVE, DELETED 2개만 존재(TIMESTAMP 없음). WebFetch로 두 번 직접 확인. https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/annotations/SoftDeleteType.html

[^sdt-71]: Hibernate ORM 7.1 Javadoc — `SoftDeleteType` enum. ACTIVE, DELETED, TIMESTAMP 3개. TIMESTAMP: "null indicates non-deleted, non-null indicates deleted at the given timestamp". WebFetch 직접 확인. https://docs.hibernate.org/orm/7.1/javadocs/org/hibernate/annotations/SoftDeleteType.html

[^where-javadoc]: Hibernate ORM 6.6 Javadoc — `@Where` 어노테이션. "Deprecated. Use SQLRestriction". WebFetch 직접 확인. https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/annotations/Where.html

[^sqlr-66]: Hibernate ORM 6.6 Javadoc — `@SQLRestriction`. "Specifies a restriction written in native SQL ... for entities or collections." "@SQLRestrictions are always applied and cannot be disabled. Nor may they be parameterized. They're therefore much less flexible than filters." WebFetch 직접 확인. https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/annotations/SQLRestriction.html

[^sqlr-since]: 위 `@SQLRestriction` javadoc의 `Since:` 태그 = 6.3. 즉 `@Where` → `@SQLRestriction` 대체는 6.3 시점. WebFetch 직접 확인. https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/annotations/SQLRestriction.html

[^pg-null]: PostgreSQL Documentation, "Comparison Functions and Operators". NULL = unknown, 비교 시 null(unknown) 반환(3치 논리), `7 = NULL` → null, `IS [NOT] DISTINCT FROM` 설명, `= NULL` 금지. WebFetch 직접 확인. (SQL 표준의 NULL/three-valued logic 의미를 설명하는 1차 벤더 문서로 인용; ANSI SQL 표준 원문 자체는 유료라 미확인) https://www.postgresql.org/docs/current/functions-comparison.html

[^fowler]: Martin Fowler, "State Machine" (DSL Catalog). "Model a system as a set of explicit states with transitions between them." 패턴이 내부 상태 분류 + 전이 원인 서술을 도움. WebFetch 직접 확인. (states/events/transitions의 세부 정의는 Fowler DSL 책 ch.51 참조로만 명시되어 본문에서 verbatim 미확인) https://martinfowler.com/dslCatalog/stateMachine.html

### 직접 fetch 실패/미확인 항목

- **Vlad Mihalcea — `@SoftDelete` / soft delete 글**: WebFetch 권한 거부 + 403으로 원문 직접 확인 실패. 본문 주장은 Thorben Janssen·Hibernate javadoc·Baeldung(검색 스니펫)으로 교차 확인된 범위만 사용. (출처 미확인)
- **Baeldung "@SoftDelete annotation" 글**: 403 Forbidden으로 원문 직접 fetch 실패. 검색 스니펫만 노출. (출처 미확인 — 본문의 사실 주장은 Baeldung 단독 근거를 쓰지 않음)
- **Hibernate 6.6 User Guide 단일 HTML(§3.12 Soft Delete, §6.9 Filtering)**: 페이지가 매우 커서 WebFetch가 중간에서 잘려 §3.12/§6.9 본문 verbatim 추출 실패. 동일 내용을 해당 javadoc(`@SoftDelete`, `SoftDeleteType`, `@SQLRestriction`, `@Where`)으로 대체 확인. User Guide 본문 verbatim은 (출처 미확인).
- **6.3 Migration Guide 본문의 `@Where` deprecation 문장**: 직접 fetch했으나 해당 문서 본문에는 `@Where` deprecation 서술이 없었음. 대신 `@SQLRestriction` javadoc의 `@since 6.3` + `@Where` javadoc의 "Deprecated. Use SQLRestriction"으로 버전을 확정. Migration Guide verbatim 문장은 (출처 미확인).
