# Payment 전체 플로우 + 모든 케이스 (Week6)

> 출처: `apps/pg-simulator` 실제 소스 + [pg-simulator-responses.md](../pg-simulator-responses.md). 추측 아님.
> 작성: 2026-06-25

## 핵심 모델 (오해 교정)

흔히 "① 응답으로 성공 처리 → ② 콜백으로 2차 확인"으로 생각하기 쉬운데 **틀리다.**

| 단계 | 무엇 | 의미 |
|---|---|---|
| ① POST 응답 | **접수(PENDING)일 뿐** | 성공 아님. 게다가 40% 500 실패·타임아웃 가능 → 못 받으면 **UNKNOWN** |
| ② 콜백 수신 | **결과 1차 확정** | SUCCESS→PAID / FAILED→FAILED. 단 **콜백은 유실 가능**(no timeout, runCatching) |
| ③ GET 조회 reconcile | **2차 안전망** | 콜백 유실·UNKNOWN을 조회로 PAID/FAILED 해소 |

→ **"응답=접수 / 콜백=확정 / 조회=안전망"**. 응답은 성공 신호가 아니다.

---

## 전체 플로우차트 (모든 케이스)

```mermaid
graph TD
    subgraph P1["① 제출 (동기) — 응답=접수일 뿐"]
        CLIENT["클라이언트"] --> SUBMIT["POST /api/v1/payments<br/>{orderId, card, amount, callbackUrl}"]
        SUBMIT --> TX1["@Tx: 주문 조회·권한·금액 확정<br/>Payment PENDING 저장"]
        TX1 --> PGCALL["PG 제출 호출 (Tx 밖)<br/>@CircuitBreaker · readTimeout"]
        PGCALL --> RESP{"제출 응답?"}
        RESP -->|"200 PENDING (~60%)"| OK200["transactionKey 저장<br/>Payment=PENDING"]
        RESP -->|"400 (우리 버그)"| E400["재시도 무의미<br/>Payment=FAILED + 알림"]
        RESP -->|"500 (40% 인위적)"| E500["제한 재시도 (멱등 전제)"]
        RESP -->|"타임아웃·무응답·CB OPEN"| TIMEOUT["결과 미확정 (in-doubt)"]
        E500 -->|"계속 실패 → CB open → Fallback"| TIMEOUT
        TIMEOUT --> UNKNOWN["Order.markUnknown()"]
        OK200 --> PENDINGRESP["클라이언트: PENDING 즉시 반환<br/>(승인 절대 안 기다림)"]
        UNKNOWN --> PENDINGRESP
        E400 --> FAILRESP["클라이언트: 실패 반환"]
    end

    subgraph P2["② 콜백 (비동기) — 결과 1차 확정"]
        PGASYNC["pg-simulator<br/>1~5s 후 비동기 결정"] --> DECIDE{"승인 분기"}
        DECIDE -->|"approve 70%"| CBOK["콜백 status=SUCCESS"]
        DECIDE -->|"한도초과 20% / 카드오류 10%"| CBNG["콜백 status=FAILED (reason)"]
        CBOK --> RECV["POST /api/v1/payments/callback 수신<br/>(인증필터 제외)"]
        CBNG --> RECV
        RECV -->|"SUCCESS"| PAID["Order.confirmPayment() → PAID"]
        RECV -->|"FAILED"| FAILED["Order.failPayment() → FAILED"]
        PGASYNC -.->|"콜백 유실 (no timeout · runCatching)"| LOST["콜백 미수신<br/>주문 PENDING/UNKNOWN 잔존"]
    end

    subgraph P3["③ reconcile (조회) — 2차 안전망"]
        RECON["reconcile 스케줄/배치<br/>대상: PENDING(콜백유실) + UNKNOWN(타임아웃)"] --> GETCALL["GET /payments/{key}<br/>or ?orderId="]
        GETCALL --> GRES{"조회 status"}
        GRES -->|"200 SUCCESS"| PAID
        GRES -->|"200 FAILED"| FAILED
        GRES -->|"200 PENDING"| RETRY2["잠시 후 재조회"]
        GRES -->|"404 (미접수 확정)"| NOTFOUND["FAILED 확정<br/>(재결제는 멱등키로 별도)"]
        NOTFOUND --> FAILED
    end

    OK200 -.->|"PG 비동기 처리 시작"| PGASYNC
    UNKNOWN --> RECON
    LOST --> RECON
    RETRY2 -.-> RECON

    classDef primary fill:#c7d2fe,stroke:#6366f1,color:#312e81
    classDef secondary fill:#ddd6fe,stroke:#8b5cf6,color:#4c1d95
    classDef success fill:#a7f3d0,stroke:#10b981,color:#064e3b
    classDef warning fill:#fde68a,stroke:#f59e0b,color:#78350f
    classDef error fill:#fecaca,stroke:#ef4444,color:#7f1d1d
    classDef info fill:#bae6fd,stroke:#0ea5e9,color:#0c4a6e
    classDef neutral fill:#e2e8f0,stroke:#64748b,color:#1e293b
    classDef accent fill:#fbcfe8,stroke:#ec4899,color:#831843

    class CLIENT,SUBMIT,TX1,PGCALL,RECV,RECON,GETCALL primary
    class PGASYNC,CBOK,CBNG info
    class RESP,DECIDE,GRES,OK200,PENDINGRESP,E500,TIMEOUT,UNKNOWN,LOST,RETRY2 warning
    class PAID success
    class E400,FAILRESP,FAILED,NOTFOUND error
```

---

## 주문 상태 머신 (OrderStatus)

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#c7d2fe', 'primaryTextColor': '#312e81', 'primaryBorderColor': '#6366f1', 'lineColor': '#6366f1', 'secondaryColor': '#ddd6fe', 'tertiaryColor': '#e2e8f0'}}}%%
stateDiagram-v2
    [*] --> PENDING_PAYMENT: 주문 생성
    PENDING_PAYMENT --> PAID: 콜백/조회 SUCCESS
    PENDING_PAYMENT --> FAILED: 콜백/조회 FAILED
    PENDING_PAYMENT --> UNKNOWN: 제출 타임아웃·CB open
    UNKNOWN --> PAID: reconcile SUCCESS
    UNKNOWN --> FAILED: reconcile FAILED / 404
    PAID --> [*]
    FAILED --> [*]
```

---

## 케이스 표 (MECE)

### 제출(POST) 응답
| # | 응답 | 조건 | 우리 대응 | 주문 상태 |
|---|---|---|---|---|
| 1 | 200 PENDING | 접수 성공(~60%) | transactionKey 저장, 결과 대기 | PENDING_PAYMENT |
| 2 | 400 | X-USER-ID 누락 / 본문검증 / 직렬화 | **우리 버그** — 재시도 X, 즉시 실패+알림 | FAILED |
| 3 | 500 | 40% 인위적 실패 / 예상외 오류 | 제한 재시도(멱등) → 계속 실패 시 CB open | (재시도 후 UNKNOWN) |
| 4 | 무응답 | 타임아웃·커넥션 실패 | **in-doubt** → markUnknown | UNKNOWN |
| 5 | CallNotPermitted | CB OPEN | Fallback → markUnknown | UNKNOWN |

### 비동기 결과 (콜백 / 조회 공통)
| 분기 | 확률 | status | 우리 매핑 |
|---|---|---|---|
| approve | 70% | SUCCESS | → PAID |
| limitExceeded | 20% | FAILED(한도초과) | → FAILED |
| invalidCard | 10% | FAILED(잘못된 카드) | → FAILED |
| 콜백 유실 | — | (미수신) | reconcile로 해소 |

### GET 조회 (reconcile)
| 응답 | 의미 | 대응 |
|---|---|---|
| 200 SUCCESS | 승인 확정 | → PAID (멱등) |
| 200 FAILED | 실패 확정 | → FAILED |
| 200 PENDING | 처리 중 | 잠시 후 재조회 |
| 404 | 미접수(접수 자체 실패) | FAILED 확정 (재결제는 멱등키로 별도) |

---

## 한 줄 요약

- **400 = 고쳐라(재시도 X)** / **404 = 조회 타이밍** / **500·타임아웃·콜백유실 = 미확정 → 조회로 확인(막 재결제 X)**
- 콜백을 **믿되 의존하지 않는다** — 유실 가능하므로 **조회 reconcile이 진짜 안전망**
- 주문 상태는 결국 **PAID · FAILED · UNKNOWN** 세 가지로 수렴
