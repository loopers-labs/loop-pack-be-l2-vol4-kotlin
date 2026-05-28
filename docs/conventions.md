# Conventions

프로젝트 전체에 적용되는 영속성/모델링 규칙. 각 도메인 설계 문서는 본 문서를 따른다.

---

## 1. FK 제약 미설정 (전체 도메인 공통)

- DB에 FOREIGN KEY 제약을 걸지 않는다. `brand_id`, `product_id`, `user_id` 등은 **논리 참조**(soft reference)로만 사용한다.
- 이유:
  - INSERT 등 쓰기 성능 확보 (FK 검증 비용 제거)
  - 향후 샤딩 / MSA 분리 대비
- 무결성은 **애플리케이션 레이어**에서 보장한다.
- Cascade 동작(예: Brand 삭제 시 Product 일괄 처리)도 DB 레벨이 아니라 **application-level cascade**로 처리한다.
- ERD에서는 mermaid 관계선은 유지하되, 각 ERD 다이어그램 아래에 다음 메모를 남긴다:
  > DB 제약(FK) 없음. 논리 참조. 무결성/cascade는 애플리케이션 레이어 책임.

---

## 2. BaseEntity 표준 컬럼 (전체 도메인 공통)

- 표준 4컬럼: `createdAt`, `updatedAt`, `createdBy`, `updatedBy`.
- 자동 채움(auditing) 한다. 현재는 `createdBy` / `updatedBy`에 어드민 헤더 식별자(`X-Loopers-Ldap` 값) 또는 사용자 헤더(`X-Loopers-LoginId`)가 저장된다. 미래에 Brand staff / Role 분리가 도입되면 자연스럽게 확장된다.
- **외부 응답 DTO에 노출하지 않는다.** 내부 메타데이터로만 사용한다.

---

## 3. 도메인 기본 삭제 정책 = Soft delete + State machine

- 도메인 entity는 hard delete 하지 않는다. `status` 컬럼(`ACTIVE` / `DELETED`)으로 상태 전이를 표현한다.
- State machine은 단순 2-state로 시작한다: `ACTIVE` ↔ `DELETED`.
  - 그 이상의 상태(`SUSPENDED`, `DRAFT` 등)는 도입 근거가 생길 때 추가한다.
- Cascade 시(예: Brand 삭제 → Product 일괄 DELETED) cascade로 영향받는 entity도 상태 전이를 따른다.

---

## 4. 예외 — Likes는 hard delete

- Likes는 토글 본질이므로 `status` 컬럼과 state machine을 두지 않는다.
- 통계/이력 책임은 별도 도메인 `LikeEvent`(append-only)가 가진다.
- 따라서 Likes 자체에 soft delete를 적용해서 생기는 redundancy를 피한다.

---

## 5. 변경 이력 누적 정책

| 도메인 | 이력 방식 | 비고 |
|---|---|---|
| Brand | `brand_history` snapshot (after-only) | CUD 시 비동기 append |
| Product | `product_history` snapshot (after-only) | CUD 시 비동기 append |
| Likes | `LikeEvent` (action = LIKE / UNLIKE) | 토글 이벤트 누적, append-only |
| Order | 미래 항목 (Order Event) | 결제 도메인 도입 시점에 같이 설계 |
| User | 미설계 | PII / 보안 정책 별도 검토 |

- history / event 적재는 **비동기**로 처리한다. 실패 시 **로깅만** 남기고 본 트랜잭션은 성공시킨다. (이번 주차에는 구현 디테일 미설계)
- history의 외부 API 노출은 없다(현 시점). 미래에 seller 도입 시 노출 여부를 검토한다.
- history 테이블의 `brand_id` / `product_id`는 본 entity가 삭제돼도 보존된다 → soft reference.

---

## 6. 이번 주차 적용 범위

- 본 문서의 규칙은 Week 2 도메인 설계(`docs/week2/scenarios/*`)에 반영된다.
- **구현 디테일**(`@Immutable`, JPA `@EntityListener`, AOP, 메시지 큐 종류 등)은 본 문서 범위가 아니다. 다음 주차 이후 결정한다.
