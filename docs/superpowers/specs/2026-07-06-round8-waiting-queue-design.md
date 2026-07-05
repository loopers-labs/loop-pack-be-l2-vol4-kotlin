# Round 8 — Redis 기반 주문 대기열 (Virtual Waiting Room) 설계

- 작성일: 2026-07-06
- 브랜치: `volume-8` (volume-7 위에서 시작, PR #107 병합 후 base=yeonjoo7로 PR 예정)
- 범위: 대기열(Step 1) + 입장 토큰·스케줄러(Step 2) + 실시간 순번 조회(Step 3) + Graceful Degradation. 통합 설계 1개.

---

## 1. 목표

트래픽 폭증(초당 100→10,000건) 시 시스템을 보호하면서 유저에게 공정한 대기 경험을 제공한다.

- **Back-pressure**: 하류(DB/PG)가 감당하는 속도·용량만큼만 주문을 흘려보낸다. 대기열은 피크를 **평탄화(smoothing)** 하는 것이지 부하를 없애는 것이 아니다.
- **공정성**: 진입 시각(FIFO) 기준 순서 보장 + 중복 진입 방지.
- **이탈 방지**: 실시간 순번 + 예상 대기 시간 피드백.
- **관문**: 대기열은 주문 API **앞단**. 주문 이후(이벤트→Kafka→metrics)는 R7 파이프라인을 그대로 재사용한다.

## 2. 현재 코드베이스 상태 (탐색 결과)

**재사용:**
- Redis: Lettuce `RedisTemplate<String,String>` 빈. `@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)`(="redisTemplateMaster")로 마스터(쓰기+즉시읽기), `opsForZSet()`/`opsForValue()` 사용. `StringRedisSerializer`. 키 컨벤션 `commerce-api:<domain>:<name>:v1:…`. `runCatching{}.onFailure{log.warn}` degradation(`ProductRedisCacheRepository` 미러). Redisson 없음.
- 스케줄러: `@EnableScheduling` 활성. `OutboxRelayScheduler`(`@Scheduled` + `@ConditionalOnProperty(name=[".."], havingValue="true", matchIfMissing=true)` 테스트 토글) 패턴.
- 주문 API: `POST /api/v1/orders`(`OrderV1Controller`), 인라인 `@RequestHeader("X-Loopers-LoginId"/"X-Loopers-LoginPw")` 인증. 인터셉터/필터 전무. `CreateOrderUsecase.execute(command)`.
- 인터페이스: `ApiResponse<T>`(success/fail), `ApiControllerAdvice`(CoreException→fail). `interfaces/api/{domain}` + `*V1Controller`/`*V1Dto`.
- 유저 식별: `UserService.getProfile(loginId,password)` → `user.id: Long`.
- 테스트: Redis Testcontainers always-on(@SpringBootTest), `opsForZSet` 검증 가능, `RedisCleanUp.truncateAll()`(flushAll). `runConcurrently`(동시성 헬퍼).

**Greenfield(신규):** 대기열 Redis 자료구조/리포지토리, 입장 토큰, 프로모트 스케줄러(leaky bucket), 순번/예상대기 API, 주문 게이트, 신규 `ErrorType`(게이트 차단).

## 3. 확정된 결정

| # | 결정 | 채택 | 근거 |
|---|---|---|---|
| D1 | 초과 트래픽 처리 | **Queuing(대기열)** | 유저가 기다릴 의사가 있는 행사 트래픽. Rate limiting(429)은 재시도 폭풍 유발 |
| D2 | 자료구조 | **Redis Sorted Set** | score=timestamp FIFO 정렬 + 원자적 연산(ZADD/ZRANK/ZPOPMIN) + TTL |
| D3 | 큐 member | **userId: Long** | 중복 진입 자동 방지(Set 특성), 과제 명시. `getProfile`로 해석 |
| D4 | 입장 제어 | **용량 기반 leaky bucket + rate 스프레드** | capacity(DB 풀)로 back-pressure, rate로 Thundering Herd 완화. 만료·소비 시 자동 재입장 |
| D5 | 토큰 게이트 위치 | **주문 엔드포인트 `@RequestHeader("X-Entry-Token")` + `EntryTokenGate`** | 인터셉터 인프라 전무 → 헤더 파라미터가 최소 diff·컨벤션 일치. `CreateOrderUsecase` 불변 |
| D6 | 실시간 피드백 | **Polling** (SSE 제외) | 구현 단순, 인프라 변경 없음. Polling 부하는 Redis(μs)로 감당 |
| D7 | Redis 장애 정책 | **Fail-open bypass** | 토큰 검증 실패 시 주문 허용(서비스 유지 우선). DB 과부하 위험은 경보로 노출 |

미채택(문서화만): 두-토큰(visitor/access) 모델 — 인증 유저라 userId가 visitor 역할, 이득 대비 복잡도↑. SSE/동적 폴링/Jitter — Nice-to-have 제외.

## 4. 아키텍처 / 흐름

```
POST /api/v1/queue/enter    (auth)                → ZADD waiting {ts}{userId}(중복방지) → 순번 응답
GET  /api/v1/queue/position (auth)                → ZRANK → 순번+예상대기 (+토큰있으면 토큰 동봉)
[QueuePromoteScheduler @100ms]  → 프룬+active산정 → admit=min(18, C−active) → ZPOPMIN admit → 토큰 발급+processing 등록
POST /api/v1/orders         (auth + X-Entry-Token)→ EntryTokenGate.validate → CreateOrderUsecase(R7) → EntryTokenGate.consume(DEL+ZREM)
Redis 장애                                         → 토큰검증 bypass(fail-open) + 경보 로그
```

## 5. Redis 자료구조 (키 `commerce-api:queue:order:*:v1`)

| 키 | 타입 | 용도 | 연산 |
|---|---|---|---|
| `…:waiting:v1` | ZSet | 대기열(진입 순서) | `ZADD`(score=진입 epoch-ms, member=userId; 이미 있으면 순번 유지), `ZRANK`(순번 0-based), `ZCARD`(전체), `ZPOPMIN N`(스케줄러) |
| `…:processing:v1` | ZSet | active 추적(leaky bucket) | `ZADD`(발급 시, score=발급 epoch-ms), `ZREM`(소비 시), `ZREMRANGEBYSCORE 0 (now−TTL)`(만료 프룬), `ZCARD`(active 수) |
| `…:token:v1:{userId}` | String, TTL 5분 | 입장 토큰 | `SET EX 300`(발급), `GET`(검증), `DEL`(소비) |

- **중복 진입 방지**: waiting ZSet member=userId → 재진입해도 한 엔트리. `ZADD ... NX`로 기존 score 보존(순번 유지).
- **active = ZCARD(processing)** — 발급됐고 아직 소비(주문완료)·만료(TTL)되지 않은 유저 수. 만료분은 스케줄러가 score 기준 프룬해 정확도 유지(토큰 TTL과 프룬 임계값 동일 300s).

## 6. Leaky bucket 입장 로직 (QueuePromoteScheduler, 100ms)

```
매 tick(fixedDelay=100ms):
  1) 프룬:   ZREMRANGEBYSCORE processing 0 (now − 300_000ms)   // 만료 토큰 자리 회수
  2) active: ZCARD processing
  3) free:   capacity(C) − active                              // C = DB 풀 = 50
  4) admit:  min(rateBatch, max(0, free))                      // rateBatch = 18 (Thundering Herd 스프레드)
  5) if admit>0: users = ZPOPMIN waiting admit                 // 원자적으로 앞 admit명
                 for each u: SET token:{u} {uuid} EX 300
                             ZADD processing {now} {u}
```
- **자동 재입장**: 토큰 소비(주문완료→ZREM+DEL) 또는 만료(TTL+프룬)로 active↓ → free↑ → 다음 tick에 그만큼 추가 발급 → 문서의 "만료·미사용 토큰만큼 다음 유저에게 추가 발급" 충족.
- `@ConditionalOnProperty(name=["queue.promoter.scheduler.enabled"], matchIfMissing=true)` — 테스트는 false로 두고 `promoteOnce()` 직접 호출(결정성). (R7 relay 패턴 동일)

## 7. 컴포넌트 (레이어)

- `domain/queue/OrderQueueRepository`(port) — **원자적 Redis 프리미티브만** 노출: `enter(userId, now): Long?`(신규진입 시 순번), `rank(userId): Long?`, `total(): Long`, `pruneExpiredProcessing(before)`, `countActive(): Long`, `popNext(n): List<Long>`(ZPOPMIN), `issueToken(userId, token, ttl)`+`addProcessing(userId, now)`, `findToken(userId): String?`, `consume(userId)`(DEL+ZREM).
  - leaky bucket 1 tick 오케스트레이션(프룬→active→`admit=min(rateBatch, C−active)`→popNext→발급)은 **`PromoteQueueUsecase.promoteOnce()`가 담당**(프리미티브 조합). 이렇게 하면 `min(...)` 산정 로직을 usecase 단위로 테스트 가능. 단일 스케줄러 스레드에서 순차 실행(원자성 프리미티브 + 단일 스레드로 충분).
- `infrastructure/queue/OrderQueueRedisRepository`: `@Qualifier(REDIS_TEMPLATE_MASTER)` + `opsForZSet/opsForValue`. 모든 연산 `runCatching` degradation.
- `application/queue`: `EnterQueueUsecase`(auth→enter→순번), `GetQueuePositionUsecase`(auth→rank/token→순번·예상대기·토큰), `PromoteQueueUsecase`(스케줄러 호출, leaky bucket), `EntryTokenGate`(validate/consume).
- `infrastructure/queue/QueuePromoteScheduler`: `@Scheduled(fixedDelay=100)` → `PromoteQueueUsecase.promoteOnce()`.
- `interfaces/api/queue/QueueV1Controller`+`QueueV1Dto`: `POST /enter`, `GET /position`.
- 주문 게이트: `OrderV1Controller.order`에 `@RequestHeader("X-Entry-Token")` 추가 → `entryTokenGate.validate(loginId,pw,token)` → `createOrderUsecase.execute(...)` → `entryTokenGate.consume(userId)`.

## 8. 예상 대기시간 · 용량/배치 산정 (문서화 요구)

```
DB 커넥션 풀: 50           → 동시 처리 상한(capacity C = 50)  ← leaky bucket 용량
주문 1건 평균: 200ms       → 이론 최대 TPS = 50 / 0.2 = 250 TPS
안전마진 70%              → 175 TPS
스케줄러 스프레드          → 100ms × 18명 (= 180/s ≈ 175 TPS, Thundering Herd 완화)
```
- 두 다이얼: **capacity C=50**(하드 동시성 상한, DB 풀 보호) + **rateBatch=18/100ms**(평시 유입률, 스파이크 방지). 평시엔 rate가 binding, 무언가 지연되면 capacity가 하드 실링.
- **예상 대기 = rank / 175** 초 (rate 기준). "약 N초/분"으로 표현(추정값 — 토큰 만료·시스템 상태로 변동).
- C·rateBatch·ttl은 프로퍼티(`queue.capacity`, `queue.rate-batch`, `queue.token-ttl`)로 외부화.

## 9. 순번/토큰 상태 (GET /position)

- waiting에 **있으면**: `position = rank+1`, `estimatedWaitSeconds`, `token=null`.
- waiting에 **없고 token 있으면**: `position = 0`, `token` 동봉("입장 가능").
- **둘 다 없으면**: `NOT_FOUND`(미진입 또는 토큰 만료/소비됨).

## 10. Graceful Degradation (Fail-open)

- **토큰 검증(게이트)**: `runCatching`, Redis 실패 → `log.warn`(경보) + **bypass(주문 허용)**. 서비스 유지 우선.
- enter/position: Redis 실패 → degrade. enter 실패해도 게이트 bypass로 주문은 계속 가능.
- 정책은 프로퍼티/문서로 명시("Redis 장애 시 우리 서비스는 어떻게 동작하는가"를 사전 정의 — 문서 강조점).

## 11. 에러 / 동시성

- 게이트 차단(토큰 없음/불일치, Redis 정상): `CoreException` + 신규 `ErrorType`(예: `TOO_MANY_REQUESTS` 또는 `FORBIDDEN`) → `ApiControllerAdvice`가 `ApiResponse.fail` 렌더. (신규 ErrorType 추가 필요 여부는 구현 시 기존 enum 확인)
- 원자성: `ZADD/ZRANK/ZPOPMIN/ZREMRANGEBYSCORE` 각각 atomic. 스케줄러 tick은 단일 스레드 순차. `ZPOPMIN N`이 원자적이라 다중 인스턴스여도 이중 발급 없음(단일 인스턴스 전제).
- 중복 진입: member=userId + `ZADD NX`로 멱등(기존 순번 보존).

## 12. 테스트 전략

각 Step TDD(Red→Green→Refactor), Redis Testcontainers.
- **Step 1**: 동시 진입(`runConcurrently`) → ZSet 순서 정확 + 중복 0(member 유일). 순번/전체 조회.
- **Step 2**: 스케줄러 `promoteOnce()` → capacity·rateBatch만큼만 토큰 발급, 초과분 waiting 유지. 토큰 TTL 만료 → 검증 실패. 소비/만료 → active↓ → 다음 tick 추가 발급. 주문 게이트 검증(유효 토큰만 통과).
- **Step 3**: 순번+예상대기 계산. 토큰 발급 후 position=0+token.
- **Degradation**: Redis 장애 시뮬(마스터 다운/예외) → 게이트 bypass(주문 허용) + 경보.

## 13. 구현 순서 (Step 1 → 2 → 3)

- **Step 1**: 대기열 Redis 자료구조 + `OrderQueueRepository`(enter/rank/total) + `EnterQueueUsecase`/`GetQueuePositionUsecase`(순번만) + `QueueV1Controller`(enter/position) + 동시 진입 테스트.
- **Step 2**: 토큰(발급/검증/소비) + processing ZSet + leaky bucket `promote` + `QueuePromoteScheduler` + 주문 게이트(`EntryTokenGate` + OrderV1Controller 헤더) + 용량/배치 산정 문서 + TTL·처리량초과 테스트.
- **Step 3**: 예상 대기시간 계산 + position 응답에 예상대기·토큰 동봉 + fail-open degradation.

각 Step 후 `analyze-concurrency`(대기열 경합)·`analyze-query`(트랜잭션 밖 Redis) 스킬로 점검.

## 14. 미결 / 리스크

- D1~D7·용량 수치는 spec 리뷰에서 재검토 가능.
- 신규 `ErrorType` 필요 여부는 기존 enum 확인 후 결정(없으면 근접 코드 재사용).
- Fail-open은 Redis 장애 시 DB 과부하 위험을 감수 — 경보/모니터링 지표(Queue Depth·Token Expiry Rate 등)로 보완 필요(문서 §운영지표).
- 다중 인스턴스 스케줄러는 이번 범위 밖(단일 전제). ZPOPMIN 원자성으로 확장 시에도 이중 발급은 없으나 rate/capacity 합산 조정 필요.
- 순번 조회 폴링 부하는 Redis로 감당하나, 대기 인원 급증 시 동적 폴링 주기(Nice-to-have, 제외)로 완화 가능 — 문서화만.
