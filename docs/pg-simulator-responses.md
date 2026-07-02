# pg-simulator 응답 케이스 (MECE) + commerce 대응

> 출처: 이 레포의 `apps/pg-simulator` 실제 소스 코드. (추측 아님)
> 작성일: 2026-06-22

pg-simulator 는 **직접연동(Key-in) 방식**의 외부 PG 를 흉내 내는 모의 서버다.
commerce 서버가 카드 정보를 담아 서버→PG 로 직접 POST 한다(브라우저 결제창 없음).

---

## 0. 공통 응답 봉투 — `ApiResponse<T>`

모든 응답은 아래 한 가지 형태로 감싸진다.

```jsonc
// 성공
{ "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": { /* 엔드포인트별 payload */ } }

// 실패
{ "meta": { "result": "FAIL", "errorCode": "<HttpStatus reasonPhrase>", "message": "<사유>" },
  "data": null }
```

- `errorCode` = `ErrorType.code` = HttpStatus 의 reasonPhrase
  → `"Bad Request"` / `"Not Found"` / `"Internal Server Error"`
- `message` = 던질 때의 `customMessage`, 없으면 `ErrorType.message`(기본 문구)

| ErrorType | HTTP status | 기본 message |
|---|---|---|
| `BAD_REQUEST` | 400 | "잘못된 요청입니다." |
| `NOT_FOUND` | 404 | "존재하지 않는 요청입니다." |
| `INTERNAL_ERROR` | 500 | "일시적인 오류가 발생했습니다." |
| `CONFLICT` | 409 | "이미 존재하는 리소스입니다." (결제 경로에선 미사용) |

---

## 1. 엔드포인트

| Method | Path | 용도 |
|---|---|---|
| POST | `/api/v1/payments` | 결제 요청(거래 생성) |
| GET | `/api/v1/payments/{transactionKey}` | 거래 단건 조회 (reconciliation) |
| GET | `/api/v1/payments?orderId=` | 주문별 거래 목록 조회 |

세 엔드포인트 모두 `X-USER-ID` 헤더 필수 (`UserInfoArgumentResolver`).

---

## 2. POST /payments — 나올 수 있는 HTTP 응답 (MECE)

처리 순서: `@RequestBody` 파싱 → `X-USER-ID` 해석 → `request.validate()` → `Thread.sleep(100~500ms)` → **40% 확률로 500 throw** → `createTransaction()` 저장(PENDING) → 200.

| # | HTTP | 발생 조건 | 응답(meta) | 우리(commerce) 대응 |
|---|---|---|---|---|
| 1 | **200** | 접수 성공(나머지 ~60%) | `result:SUCCESS`, `data:{transactionKey, status:"PENDING", reason:null}` | Payment PENDING 저장 → 콜백/조회로 **결과 대기** |
| 2 | **400** | `X-USER-ID` 헤더 누락 | `errorCode:"Bad Request"`, `message:"유저 ID 헤더는 필수입니다."` | **우리 버그** — 재시도 무의미, 즉시 실패+알림 |
| 3 | **400** | 본문 검증 실패(`validate`) | orderId<6 / 카드번호 형식 / amount≤0 / callbackUrl prefix | 동일 — 요청 고쳐야 함, 재시도 X |
| 4 | **400** | JSON 파싱·enum·필수필드 오류 | `"필드 'cardType'… 사용 가능한 값 : [SAMSUNG, KB, HYUNDAI]"` 등 | 동일 — 직렬화 버그, 재시도 X |
| 5 | **500** | **40% 인위적 실패** | `errorCode:"Internal Server Error"`, `message:"현재 서버가 불안정합니다. 잠시 후 다시 시도해주세요."` | **재시도 가능(5xx)** — 멱등 전제 제한 재시도 → 계속 실패 시 CB open → Fallback → **UNKNOWN** |
| 6 | **500** | 예상치 못한 Throwable | `message:"일시적인 오류가 발생했습니다."`(기본) | 5와 동일 처리 |
| 7 | **(응답 없음)** | 네트워크 타임아웃·커넥션 실패 | — (HTTP 응답 자체가 없음) | **in-doubt → UNKNOWN** → 조회로 확정 |

### validate() 가 던지는 400 메시지 (정확한 문구)

| 조건 | message |
|---|---|
| `orderId.isBlank() || orderId.length < 6` | "주문 ID는 6자리 이상 문자열이어야 합니다." |
| 카드번호 `^\d{4}-\d{4}-\d{4}-\d{4}$` 불일치 | "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다." |
| `amount <= 0` | "결제금액은 양의 정수여야 합니다." |
| `callbackUrl` 가 `http://localhost:8080` 로 시작 안 함 | "콜백 URL 은 http://localhost:8080 로 시작해야 합니다." |

---

## 3. 비동기 결과 모델 — POST 200 이후

POST 200 은 **"접수됨(PENDING)"** 일 뿐, 승인 결과가 아니다.
실제 승인/실패는 `PaymentApplicationService.handle()` 에서 **비동기**로 결정되고,
결과는 (a) `callbackUrl` 로 콜백, (b) GET 조회로 확인할 수 있다.

| 분기 | 확률 | 결과 status | reason | 우리 매핑 |
|---|---|---|---|---|
| `approve()` | 70% (31~100) | `SUCCESS` | null | → **PAID** |
| `limitExceeded()` | 20% (1~20) | `FAILED` | 한도 초과 | → **FAILED** |
| `invalidCard()` | 10% (21~30) | `FAILED` | 잘못된 카드 | → **FAILED** |

> ⚠️ 콜백(`paymentRelay.notify`)은 `runCatching` 으로 실패를 삼키고 **타임아웃도 없다 → 유실 가능**.
> 콜백만 믿으면 안 되고, GET 조회(reconciliation)가 안전망이다.

---

## 4. GET 조회 응답

### GET /payments/{transactionKey}

| HTTP | 조건 | data / message | 우리 대응 |
|---|---|---|---|
| **200** `status:PENDING` | 아직 처리 중 | `TransactionDetailResponse` | 잠시 후 재조회 |
| **200** `status:SUCCESS` | 승인됨 | reason:null | → **PAID** 확정 (멱등) |
| **200** `status:FAILED` | 한도초과/카드오류 | reason 채워짐 | → **FAILED** 확정 |
| **404** | 없는 transactionKey | "(transactionKey: …) 결제건이 존재하지 않습니다." | 키 오류면 조사 / 생성 직후면 잠시 후 재조회 |
| **400** | `X-USER-ID` 누락 | "유저 ID 헤더는 필수입니다." | 우리 버그 |

### GET /payments?orderId=

| HTTP | 조건 | 우리 대응 |
|---|---|---|
| **200** | `data:{orderId, transactions:[{transactionKey,status,reason}…]}` | 주문에 엮인 거래들 상태 확인 |
| **404** | 해당 주문 결제건 없음 ("(orderId: …) 에 해당하는 결제건이 존재하지 않습니다.") | 조사 |
| **400** | `X-USER-ID` 누락 | 우리 버그 |

---

## 5. 핵심 — "각각 대비해서 반응"

HTTP status 를 **재시도 가능 / 불가 / 미확정** 으로 가르는 게 대응의 뼈대다.

- **400 (Bad Request) = 우리 잘못** → 재시도 절대 무의미. 즉시 실패 + 로깅/알림. (고쳐야 풀림)
- **404 = 조회 시점 이슈** → 생성 직후면 짧게 재조회, 진짜 없으면 예외.
- **500 / 타임아웃 / 콜백유실 = "실패"가 아니라 "결과 미확정(in-doubt)"** ⚠️
  - **결제가 됐을 수도 있다.** 그래서 **무작정 새 결제 재요청 금지** → 멱등키 또는 **조회로 실제 상태 확인**.
  - 제한 재시도(지수 백오프) → 계속 실패면 **CircuitBreaker open → Fallback → 주문 `markUnknown()`** + 사용자에겐 정상 응답.
  - 이후 **조회 reconciliation 으로 UNKNOWN 을 PAID/FAILED 로 해소**.

> 한 줄 요약: **400=고쳐라(재시도 X) / 404=조회 타이밍 / 500·타임아웃·콜백유실=미확정이니 조회로 확인(막 재결제 X)**.
> 그래서 우리 주문 상태는 결국 **PAID · FAILED · UNKNOWN** 세 가지로 수렴한다.

---

## 참고 (레포 내 소스)

- `apps/pg-simulator/src/main/kotlin/com/loopers/interfaces/api/payment/PaymentApi.kt`
- `apps/pg-simulator/src/main/kotlin/com/loopers/interfaces/api/payment/PaymentDto.kt`
- `apps/pg-simulator/src/main/kotlin/com/loopers/application/payment/PaymentApplicationService.kt`
- `apps/pg-simulator/src/main/kotlin/com/loopers/interfaces/api/ApiControllerAdvice.kt`
- `apps/pg-simulator/src/main/kotlin/com/loopers/support/error/ErrorType.kt`
- `apps/pg-simulator/src/main/kotlin/com/loopers/interfaces/api/ApiResponse.kt`
- `apps/pg-simulator/src/main/kotlin/com/loopers/interfaces/api/argumentresolver/UserInfoArgumentResolver.kt`
</content>
</invoke>
