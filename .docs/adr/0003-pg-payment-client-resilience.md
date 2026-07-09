# ADR-0003 — PG(외부 결제) 연동: HTTP 클라이언트 & 회복탄력성 선택 (Feign + Spring Cloud Circuit Breaker)

| | |
|---|---|
| **상태** | Accepted (기술 선택 확정, 구현 전) |
| **날짜** | 2026-06-22 |
| **결정자** | rojojun |
| **관련** | week6 외부 연동 분석 [`system_error_scenario.md`](../wekk6/system_error_scenario.md), `pg-simulator` 연동 |
| **선행** | [ADR-0001 재고 동시성](0001-order-stock-concurrency.md), [ADR-0002 캐싱](0002-product-list-caching.md) (재고·최종가는 캐싱 제외 → 결제 시 실시간 확인) |

> ⚠️ 이 문서는 *"PG를 호출할 HTTP 클라이언트로 RestTemplate / WebClient / FeignClient 중 무엇을 쓸까?"* 라는 질문에서 시작해, **"비동기"라는 단어에 섞여 있던 두 개념을 분리**하고, 회복탄력성·의존성 비용까지 따져 **Feign + Spring Cloud Circuit Breaker(Resilience4j)** 로 수렴한 대화를 기록한 것이다. 결론보다 **왜 그 결론에 도달했는지**가 중요하다.

---

## 1. Context (배경)

### 출발 질문
"외부 PG 모듈을 호출하기 좋은 클라이언트는 RestTemplate / WebClient / FeignClient 중 무엇인가?"

### 코드/실측 사실 (조사 결과)
- 우리 앱 스택: `commerce-api` 는 `spring-boot-starter-web` = **servlet(MVC) + 톰캣 스레드풀**. webflux/feign 의존성 없음. **Spring Boot 3.4.4 (Spring Framework 6.2)**.
- PG 모듈 실측([`system_error_scenario.md`](../wekk6/system_error_scenario.md) §6): `POST /api/v1/payments` 는 **100~500ms에 끝나고 즉시 `PENDING` 반환**, 결과(SUCCESS/FAILED)는 **1~5초 뒤 콜백**으로 전달. 요청 단계 **40% 실패**. 멱등키 없음. 콜백은 fire-and-forget(재시도 0).
- 우리가 PG를 부르는 패턴: ① 결제 요청(단발) ② 폴링 조회(단발). 둘 다 트랜잭션 밖, 요청-응답 1회. 엔드포인트 **3개**(요청/단건조회/목록조회).

### 처음의 오해 ① — "비동기" 한 단어에 두 개념이 섞임
> "비동기로 할 거야. 유저가 결제를 500ms 동기로 기다릴 이유가 없잖아. WebClient가 논블로킹이니 그게 맞지 않나?"

여기서 **"비동기"가 두 개**였다:
- **비동기 ①** — 유저를 동기적으로 안 기다리게 한다 *(워크플로우/애플리케이션 레벨)*
- **비동기 ②** — HTTP 클라이언트가 논블로킹이다 *(IO 레벨 = WebClient)*

원했던 건 ①(UX)인데, 그걸 ②(클라이언트 교체)로 달성하려 했다. **둘은 독립이다.**

### 처음의 오해 ② — "Resilience/retry 할 거니까 Feign"
retry·서킷브레이커가 **클라이언트에 종속**된다고 본 것. 실제로 Resilience4j(`@Retry`, `@CircuitBreaker`)는 RestTemplate·RestClient·WebClient·Feign **어디에든** 붙는다. retry는 Feign 선택의 근거가 못 된다.

### 처음의 오해 ③ — "Resilience4j 쓸 거면 Spring Cloud BOM 도입은 당연"
**Resilience4j는 Spring Cloud 소속이 아니다.** 독립 라이브러리(Hystrix 후계)이며 `resilience4j-spring-boot3` 로 **Cloud 없이** 쓸 수 있다. BOM을 변호하는 건 Resilience4j가 아니라 **Feign**이다.

### 핵심 통찰 ⭐
> **사고실험**: 백그라운드 워커 스레드에서 RestTemplate(완전 동기 블로킹)으로 PG를 부른다. 유저는 기다리는가? — **안 기다린다.** 유저를 안 기다리게 한 건 *"PG 호출을 유저 스레드에서 떼어냈다"* 는 구조(①)이지, 클라이언트의 논블로킹(②)이 아니다.
>
> 따라서 **②(WebClient 논블로킹)의 유일한 실익은 "백그라운드 스레드 자원 절약"** 이고, 그건 동시 외부호출이 스레드풀을 고갈시킬 **대규모일 때만** 값을 한다. 우리 규모는 아니다 → ② 불필요.
>
> **PG 호출 = 백그라운드의 짧은 동기 호출** 로 확정되면, 남는 싸움은 *"동기 클라이언트 중 뭐가 깔끔하냐"* 뿐이다.

---

## 2. Decision Drivers (결정 기준)

1. **UX(①)는 워크플로우로 달성**: 유저 비대기는 클라이언트가 아니라 "PG 호출을 유저 흐름에서 분리"로 푼다.
2. **스택 정합성**: servlet(MVC) + 톰캣 스레드풀 위에서 리액티브(②)는 의존성·사고비용만 늘고 이득이 없다.
3. **외부 불확실성 대응**: 40% 실패 + 5초 지연 → 타임아웃·재시도·서킷브레이커·폴백이 *진짜* 난이도. (클라이언트 종류와 무관)
4. **선언성/표준화**: 외부 연동이 늘어날 것을 전제로, HTTP 호출 보일러플레이트를 줄이고 일관된 패턴을 깐다.
5. **의존성 비용은 보고 산다**: BOM = 버전 부품표. Spring Cloud BOM은 **Boot↔Cloud 버전 종속**을 부과한다. 분산(amortization) 가능할 때만 정당.

---

## 3. 핵심 결정 사항 (Decisions)

### D0. 결제는 "동기 대기"가 아니라 **비동기 워크플로우** — 단, 구조로 달성한다
- 주문을 `PENDING`으로 만들고 유저에게 즉시 응답. PG 호출은 유저 요청 스레드 **밖**(백그라운드/이벤트)에서 수행. 결과는 **콜백 + 폴링**으로 수렴(§ `system_error_scenario.md` 시나리오 ②: 콜백 단독 불가, 폴링 필수).
- **왜**: 유저 비대기(①)는 클라이언트 종류가 아니라 호출 위치 분리로 달성된다.

### D1. IO 논블로킹(WebClient)은 **채택하지 않는다**
- 우리 스택은 servlet, 호출 패턴은 백그라운드 단발. `.block()`으로 쓰면 톰캣/워커 스레드를 그대로 점유 → RestTemplate 대비 실익 없이 webflux 의존성·리액티브 사고비용만 추가.
- **재검토 트리거**: 동시 외부호출이 스레드풀을 고갈시키는 규모가 되면 ②를 다시 본다.

### D2. HTTP 클라이언트 = **FeignClient** (Spring Cloud OpenFeign)
- **왜**: Feign의 본질은 retry도 비동기도 아닌 **'선언(declarative)'**. 인터페이스 + 애노테이션으로 명세하면 **동적 프록시**가 URL 조립·직렬화·응답 파싱 구현을 생성. 공통 헤더(`X-USER-ID` 등)는 **`RequestInterceptor`** 한 곳에 박아 전 호출 자동 적용.
- **이득의 스케일**: *외부 API가 많을수록 × 공통 규약이 빡셀수록* 커진다. 지금 PG는 1개·3엔드포인트라 단독 이득은 작지만, **외부 연동 표준화의 기반**으로 채택.
- RestTemplate(maintenance mode), RestClient(신형 동기 fluent)도 후보였으나 D4의 amortization 논리로 Feign 선택(아래 표).

### D3. 회복탄력성 = **Spring Cloud Circuit Breaker + Resilience4j** (경로 1: Cloud 통일)
- 40% 실패·5초 지연 대응을 **타임아웃/재시도/서킷브레이커/폴백**으로. 구현은 Resilience4j, **추상화는 Spring Cloud Circuit Breaker** 를 통해.
- **왜 직접(`resilience4j-spring-boot3`)이 아니라 Cloud 경로인가**: Feign이 이미 Spring Cloud 생태계를 들이므로, 회복탄력성도 Cloud Circuit Breaker로 통일하면 **같은 BOM을 공유** → 비용 분산(D4). 구현체 교체도 추상화 뒤에서 가능.

### D4. **Spring Cloud BOM 비용(Boot↔Cloud 버전 종속)을 수용** — Feign+CB 공유로 정당화
- BOM은 코드 생성기가 아니라 **버전 호환 세트(부품표)**. Spring Cloud BOM은 Boot 버전과 **호환 매트릭스**가 있다 → Boot 3.4.4 ↔ **Spring Cloud 2024.0.x**. Boot 업그레이드 시 Cloud 동반 관리가 비용.
- **amortization**: Feign(`spring-cloud-starter-openfeign`) + Circuit Breaker(`spring-cloud-starter-circuitbreaker-resilience4j`)가 **둘 다 Cloud BOM 식구** → BOM 한 번 들여 둘이 나눠 쓰므로 본전. (Resilience4j 단독은 BOM 핑계가 못 됨)

### D5. Feign의 **동기 블로킹**은 단점이 아니라 정합점
- Spring Cloud OpenFeign 공식 구현은 동기 블로킹(리액티브 Feign은 비주류). D0~D1에서 PG 호출을 "백그라운드 짧은 동기 호출"로 확정했으므로, Feign의 동기성은 오히려 아키텍처와 **맞아떨어진다.**

---

## 4. Considered Options & Trade-offs (검토와 트레이드오프 요약)

| 결정 | 고른 것 | 버린 대안 | 트레이드오프 / 왜 |
|---|---|---|---|
| 유저 비대기 | 워크플로우 분리(①) | 클라이언트 논블로킹(②)으로 달성 | ①은 구조 문제. ②는 UX와 무관, 스레드 절약(대규모)일 때만 의미 |
| IO 모델 | 동기 블로킹 | WebClient 논블로킹 | servlet+백그라운드 단발에선 ② 이득 0. webflux 의존성/사고비용만↑ |
| HTTP 클라이언트 | **Feign** | RestTemplate / RestClient | RestTemplate=maintenance. RestClient=의존성0이나 표준화·amortization에서 Feign 우위 |
| 회복탄력성 경로 | **Cloud Circuit Breaker** | Resilience4j 직접(`resilience4j-spring-boot3`) | 직접=BOM 불필요·가벼움. Cloud=Feign과 BOM 공유·일관·구현 교체 용이 |
| 의존성 비용 | Cloud BOM **수용** | Cloud 회피(RestClient+직접 R4j) | 수용=Boot↔Cloud 버전 종속. 회피=최소 의존성이나 표준화 포기 |

> **핵심 분기**: "외부 연동이 앞으로 늘어난다(표준화 가치)" 가 참이면 Cloud 통일(이 ADR)이 우월. 거짓이면 *RestClient + Resilience4j 직접*(경로 2)이 더 가볍고 옳다.

---

## 5. Consequences (결과)

### 긍정
- 선언적 클라이언트로 호출 보일러플레이트↓, **외부 연동 표준화의 기반** 확보.
- 회복탄력성을 Cloud Circuit Breaker 추상화 뒤에 둬 40% 실패·지연에 폴백/차단 가능, 구현체 교체 유연.
- Feign·CB가 동일 Cloud 생태계 → 패턴·설정 일관, BOM 비용 분산.
- 동기 블로킹 클라이언트가 백그라운드 호출 아키텍처(D0)와 충돌 없음.

### 부정 / 리스크
- **Boot↔Cloud 버전 종속**: Boot 업그레이드 시 Spring Cloud 2024.0.x 호환표 확인이 상시 부담.
- **의존성 무게/추상화 한 겹**: Feign 프록시·Cloud 레이어로 디버깅 시 마법 같은 구간 발생.
- PG가 **단일·3엔드포인트**라 Feign 단독 이득은 현재 작음 → 표준화 전제가 깨지면 과투자.
- 동기 호출이라 대규모 동시 결제 시 백그라운드 스레드 점유 → 그 시점엔 ②(논블로킹) 재검토 필요.

### 재검토 트리거
- 외부 연동이 PG에 머물고 늘지 않음 → Feign/Cloud 이득 미미, 경로 2로 회귀 검토.
- 동시 외부호출이 스레드풀 고갈 수준 → WebClient(②) 또는 비동기 메시징 도입.
- Boot↔Cloud 버전 호환 충돌이 잦음 → Cloud 의존 최소화 재검토.

---

## 6. 적용 방향 (구현은 직접)
```
[유저 요청] 주문 생성 → PENDING 저장 → 유저에게 즉시 응답 (①)
     │
     └─(트랜잭션 밖/백그라운드)→ PaymentPort(Feign 인터페이스)로 PG 요청
            ├─ RequestInterceptor: 공통 헤더(X-USER-ID 등) 자동 주입
            ├─ Circuit Breaker(Resilience4j): timeout / retry / fallback / open
            └─ 결과 수렴:
                 ├─ 콜백 수신 API (push) — 단독 신뢰 금지
                 └─ 폴링/스케줄러 (pull) — PENDING 탈출 보장 (system_error_scenario ②)
멱등성: orderId 기준, 재시도/중복 요청에도 결제건 1개 (system_error_scenario ①④)
```

---

## 7. 한 줄 요약
> "비동기"는 **워크플로우(①)** 와 **IO 논블로킹(②)** 으로 갈린다. 유저 비대기는 ①(구조)로 달성하고, 우리 스택에선 ②가 불필요하다. PG 호출은 **백그라운드 짧은 동기 호출** 이므로 동기 클라이언트로 충분하며, **선언성·표준화**를 위해 **Feign**, 회복탄력성은 같은 Cloud 생태계의 **Circuit Breaker(Resilience4j)** 로 통일한다. 그 대가인 **Boot↔Cloud 버전 종속**은 Feign+CB의 BOM 공유로 분산되므로 수용한다.

---

## 8. 후속 작업 (Follow-ups)
- [ ] **버전 확정**: Boot 3.4.4 ↔ Spring Cloud **2024.0.x** BOM 추가, 호환 검증.
- [ ] **PaymentPort(Feign) 설계**: 요청/단건조회/목록조회 3개 메서드, DTO 매핑.
- [ ] **RequestInterceptor**: `X-USER-ID` 등 공통 헤더 주입.
- [ ] **Circuit Breaker 설정값**: connect/read timeout, 재시도 횟수·백오프, 슬라이딩 윈도우, open 임계치, **fallback 동작**(PENDING 유지 + 폴링 위임).
- [ ] **멱등성 설계**: `orderId` 기준 진행 중 결제 1건 제약 (system_error_scenario ①④).
- [ ] **결과 수렴 이중화**: 콜백 수신 API + 폴링 스케줄러 (system_error_scenario ②③).
- [ ] **트랜잭션 경계**: PG 호출을 주문 DB 트랜잭션 밖으로 (선커밋 후 외부 호출).
- [ ] **관측**: OTEL로 PG 호출 span·CB 상태·재시도 추적 (이미 trace 전파 확인됨).
