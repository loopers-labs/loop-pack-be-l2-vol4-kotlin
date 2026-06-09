# PR #44 CodeRabbit 후속 작업 백로그

> **출처 PR**: [loopers-labs/loop-pack-be-l2-vol4-kotlin#44](https://github.com/loopers-labs/loop-pack-be-l2-vol4-kotlin/pull/44)
> **작성 시점**: week3 도메인 구현 PR 머지 직전
> **방침**: 1건(data.sql 평문)만 이 PR에서 처리. **나머지 18건은 다음 PR에서 해결**.
> 각 항목의 "처리방향"은 1차 판단이며, 다음 PR 착수 시 코드 대조 후 확정한다.
> 처리방향 범례: ✅완료 · 🔧채택(고침) · ⚖️트레이드오프(답변) · 🔍추가검토

---

## 0. 처리 현황 요약

| # | 심각도 | 위치 | 요지 | 처리방향 |
|---|---|---|---|---|
| 1 | Major | `account-api/.../data.sql:3` | 평문 관리자 비밀번호 주석 노출 | ✅ 완료 (이 PR) |
| 2 | Major | `commerce-api/build.gradle.kts:27` | H2 전환 시 MySQL 전용 동작 검증 보강 | 🔍 추가검토 |
| 3 | Major | `application/brand/BrandFacade.kt:22` | 대량 브랜드 삭제 벌크/청크 + 트랜잭션 타임아웃 | ⚖️ 트레이드오프 유력 |
| 4 | Major | `application/like/LikeEventHandler.kt:46` | 경고 로그에 userId/productId 직접 노출 (PII) | 🔧 채택 |
| 5 | Major | `domain/like/ProductLikeRepository.kt:12` | 좋아요 목록 조회 페이지네이션/커서 제한 (port) | 🔧/⚖️ (8과 묶음) |
| 6 | Major | `infrastructure/.../inventory/InventoryJpaRepository.kt:17` | 비관적 쓰기 락에 락 타임아웃 힌트 추가 | 🔧 채택 |
| 7 | Major | `infrastructure/.../like/ProductLikeJpaRepository.kt:11` | 사용자 전체 좋아요 목록 페이징 없음 (OOM) | 🔧/⚖️ (5와 묶음) |
| 8 | Major | `infrastructure/.../order/OrderJpaRepository.kt:8` | 날짜 범위 조회 건수 제한 없음 (OOM) | 🔧 채택 (Pageable) |
| 9 | Major | `infrastructure/.../order/OrderJpaRepository.kt:9` | Order.items 컬렉션 조회 N+1 | 🔧 채택 |
| 10 | Major | `infrastructure/.../product/ProductJpaRepository.kt:25` | 비동기 like_count 갱신 행수 처리 + 불일치 복구 | 🔍 부분채택 (복구배치는 YAGNI 가능) |
| 11 | Major | `application/CommerceApiApplication.kt:10` | `@EnableAsync` bounded TaskExecutor + 예외 핸들러 | 🔧 채택 |
| 12 | Major | `test/.../brand/BrandFacadeTest.kt:25` | 실패 케이스 테스트 추가 | 🔧 채택 |
| 13 | Minor | `application/product/ProductLikeCountEventHandler.kt:43` | 예외 로그에 throwable 전달해 stack trace 보존 | 🔧 채택 (간단) |
| 14 | Minor | `test/.../like/LikeEventHandlerTest.kt:54` | onUnliked 실패 전파 방지 테스트 고정 | 🔧 채택 |
| 15 | Minor | `test/.../like/LikeServiceTest.kt:47` | 저장된 ProductLike 필드값 검증 추가 | 🔧 채택 |
| 16 | Minor | `test/.../product/ProductLikeCountEventHandlerTest.kt:41` | 예외 시 로깅 동작 검증 | ⚖️ 선택 (과한 검증 여지) |
| 17 | Minor | `test/.../order/OrderRepositoryIntegrationTest.kt:39` | `found!!` 반복을 `requireNotNull`로 정리 | 🔧 채택 (스타일) |
| 18 | Minor | `test/.../support/DatabaseCleanup.kt:37` | 정리 효과 검증 부족 | 🔍 추가검토 |
| 19 | Minor | `docs/week3/03-architecture.html:848` | Mermaid 제네릭 표기 미이스케이프 | 🔧 채택 (간단) |

---

## 1. Major 상세 + 1차 판단

### 2. build.gradle.kts:27 — H2 전환 시 MySQL 검증
- **CodeRabbit**: 통합 테스트 DB를 H2(`MODE=MySQL`)로 돌리면 MySQL 전용 동작(락, 제약, 함수)이 검증되지 않는다.
- **1차 판단(🔍)**: 과제 범위·실행 속도상 H2 선택은 합리적. 단 PESSIMISTIC_WRITE 락 동작은 H2에서 의미가 약함 → 핵심 동시성 시나리오만 Testcontainers MySQL로 보강할지 다음 PR에서 판단. 전면 전환은 YAGNI.

### 3. BrandFacade.kt:22 — 대량 삭제 벌크/타임아웃
- **CodeRabbit**: `softDeleteByBrand`가 브랜드 산하 상품을 일괄 soft delete할 때 건수가 크면 벌크 update/청크 + 트랜잭션 타임아웃이 필요.
- **1차 판단(⚖️)**: 현재 과제 데이터 규모에서 단건 루프 soft delete는 문제 없음. 대량 벌크는 **투기적 최적화(YAGNI)**. "현재 규모에서는 불필요, 규모 커지면 벌크 update로 전환"으로 답변 유력. 단 한 트랜잭션 내 N건 update의 타임아웃 가드만 가볍게 검토.

### 4. LikeEventHandler.kt:46 — 로그 PII
- **CodeRabbit**: 경고 로그에 userId/productId를 직접 남기면 운영 로그에 식별자 누수.
- **1차 판단(🔧)**: CLAUDE.md §4 PII 정책과 정합. 식별자 마스킹/제거 또는 로그 레벨·내용 조정. 채택.

### 5/7. 좋아요 목록 페이징 (port + adapter)
- **CodeRabbit**: 사용자 전체 좋아요 목록을 페이징 없이 전부 조회 → 다건 시 OOM.
- **1차 판단(🔧/⚖️)**: port(`ProductLikeRepository`)와 adapter(`ProductLikeJpaRepository`) 동시 수정 필요. "본인 좋아요 목록"은 실사용상 페이징이 맞음 → 커서/Pageable 도입 채택. 단 과제 스펙이 단순 목록이면 트레이드오프 답변 가능. **함께 처리**.

### 6. InventoryJpaRepository.kt:17 — 락 타임아웃 힌트
- **CodeRabbit**: PESSIMISTIC_WRITE에 락 획득 타임아웃 힌트가 없어 무한 대기 가능.
- **1차 판단(🔧)**: `@QueryHints(@QueryHint(name="jakarta.persistence.lock.timeout", value="..."))` 추가. 데드락/무한대기 방지. 비용 낮고 효과 명확. 채택.

### 8. OrderJpaRepository.kt:8 — 날짜 범위 무제한 조회
- **1차 판단(🔧)**: `orderedAtBetween` 조회에 Pageable/limit 추가. 채택.

### 9. OrderJpaRepository.kt:9 — Order.items N+1
- **CodeRabbit**: Order 목록 조회 시 items 컬렉션 lazy 로딩으로 N+1.
- **1차 판단(🔧)**: fetch join / `@EntityGraph` / `@BatchSize` 중 조회 패턴에 맞게 선택. 채택. 단 실제 items 접근 경로 확인 후.

### 10. ProductJpaRepository.kt:25 — like_count 비동기 갱신
- **CodeRabbit**: 원자적 update의 갱신 행수(0건=대상 없음) 처리 + count 불일치 복구.
- **1차 판단(🔍)**: 갱신 행수 로깅/검증은 가볍게 채택. **불일치 복구 배치는 현시점 YAGNI** → 답변으로 보류. 부분 채택.

### 11. CommerceApiApplication.kt:10 — @EnableAsync 구성
- **CodeRabbit**: 기본 `SimpleAsyncTaskExecutor`는 스레드 무제한 생성, Async 예외가 삼켜짐.
- **1차 판단(🔧)**: bounded `ThreadPoolTaskExecutor` + `AsyncUncaughtExceptionHandler` 구성. AFTER_COMMIT 비동기 리스너 운영 안정성과 직결. 채택.

### 12. BrandFacadeTest.kt:25 — 실패 케이스 테스트
- **1차 판단(🔧)**: 정상 호출 순서만 검증 중 → 예외/실패 경로 테스트 추가. 채택.

---

## 2. Minor 일괄 처리 후보 (다음 PR에서 빠르게)
- 13, 14, 15, 17, 19: 단순/저비용 → 일괄 채택
- 16: 로깅 동작 검증은 과한 결합 여지 → 선택
- 18: DatabaseCleanup 정리 효과 검증 → 추가검토

---

## 3. 다음 PR 권장 묶음 (커밋/작업 단위)
1. **보안·로그 PII**: 4, 13
2. **조회 한계(페이징/N+1)**: 5+7, 8, 9
3. **동시성·비동기 안정성**: 6, 11, 10(부분)
4. **테스트 보강**: 12, 14, 15, 16, 17
5. **문서/기타**: 19, 2·3·18 답변 정리
