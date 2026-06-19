### TL;DR

조건부 atomic update 기반으로 예약 재고를 관리해, 결제를 했는데도 재고가 없어 상품을 받을 수 없는 상황을 방지한다. 조건 검증과 수량 변경을 한 SQL 문으로 처리하고, affected row가 0이면 변경된 튜플이 없다는 사실로 실패를 즉시 판단한다. 락 경합 상황에서도 현재 요청 안에서 과한 재시도 루프를 만들지 않아 응답 시간을 불필요하게 지연시키지 않는다.

### 본문

# 조건부 Atomic Update 기반 재고 예약 설계

## 1. 배경

재고 관리에서 가장 중요한 목표 중 하나는 재고가 없는데도 결제가 이루어지는 것을 막는 것이다.

사용자가 결제를 완료했는데 실제로는 제공할 수 있는 재고가 없다면, 단순한 주문 실패가 아니라 서비스 신뢰를 잃는 문제가 된다. 특히 결제 이후에 "상품을 받을 수 없다"는 사실을 알게 되는 경험은 사용자에게 매우 불쾌하게 다가갈 수 있다.

따라서 주문/결제 흐름에서는 결제 전에 판매 가능한 재고를 확보하고, 결제 완료 후에는 확보한 재고를 실제 판매 재고 차감으로 확정해야 한다.

이번 설계에서는 이를 위해 실제 재고와 예약 재고를 분리하고, 모든 재고 변경을 조건부 atomic update로 처리한다.

---

## 2. 핵심 설계 방향

| 책임 | 설계 |
| --- | --- |
| 실제 판매 재고 | `stock_quantity` |
| 결제 전 확보한 예약 재고 | `reserved_quantity` |
| 가용 재고 계산 | `stock_quantity - reserved_quantity` |
| 재고 변경 방식 | 조건부 atomic update |
| 동시성 충돌 판단 | affected row 수 검증 |
| 실패 처리 | 현재 DB 트랜잭션 rollback |

핵심 방향은 다음과 같다.

- 실제 재고와 예약 재고를 하나의 `product_stock` 테이블 튜플에서 함께 관리한다.
- 주문 생성 시 실제 재고를 바로 차감하지 않고 `reserved_quantity`를 증가시킨다.
- 결제 완료 후 `stock_quantity`와 `reserved_quantity`를 함께 차감한다.
- 예약 만료나 결제 전 취소 시 `reserved_quantity`를 반환한다.
- 결제 후 취소 시 `stock_quantity`를 복구한다.
- 이 모든 변경은 DB에서 조건부 atomic update로 수행해 조건 검증과 수량 변경을 하나의 SQL 문으로 묶는다.
- 변경되어야 하는 튜플이 변경되지 않으면 affected row 수로 실패를 즉시 판단하고 현재 트랜잭션을 rollback한다.

---

## 3. 왜 Atomic Update인가

재고 예약, 반환, 확정에서 가장 중요한 판단은 "지금 이 변경이 실제로 반영되었는가"다.

예를 들어 같은 상품에 대해 다음 두 예약이 동시에 들어왔다고 가정한다.

```text
주문 A: reserved_quantity + 2
주문 B: reserved_quantity + 1
```

두 연산은 순서가 바뀌어도 최종 결과가 같다.

```text
+2 후 +1 = +3
+1 후 +2 = +3
```

이처럼 수량 증가/감소는 조건만 만족한다면 실행 순서보다 변경 시점의 조건 충족 여부가 더 중요하다.

따라서 핵심은 애플리케이션이 먼저 조회한 값을 믿고 다시 저장하는 것이 아니라, DB가 한 문장 안에서 조건 검증과 변경을 함께 수행하게 하는 것이다.

조건을 만족하지 못하면 변경된 튜플 수가 0이므로 애플리케이션은 실패를 바로 알 수 있다. 락 경합이 발생한 경우에도 실패 여부를 모호하게 둔 채 현재 요청 안에서 과한 재시도 루프를 만들지 않아, 응답 시간을 불필요하게 지연시키지 않는다.

예약 생성은 다음 조건을 만족할 때만 성공한다.

```sql
UPDATE product_stock
   SET reserved_quantity = reserved_quantity + :quantity
 WHERE product_id = :productId
   AND deleted_at IS NULL
   AND stock_quantity - reserved_quantity >= :quantity;
```

이 쿼리는 두 가지를 동시에 보장한다.

1. 현재 가용 재고가 요청 수량 이상인지 확인한다.
2. 조건을 만족하는 경우에만 예약 수량을 증가시킨다.

즉, 애플리케이션에서 "조회 후 판단 후 수정"을 분리하지 않아 lost update가 발생하지 않는다.

실행 결과 affected row가 1이면 예약 성공이고, 0이면 조건을 만족하지 못해 실패한 것이다.

---

## 4. Optimistic Locking을 선택하지 않은 이유

Optimistic locking은 보통 row의 version을 읽고, 수정 시 version이 그대로인지 확인한다.

이 방식은 여러 트랜잭션이 같은 row를 동시에 수정하면 실제 변경 조건이 아직 유효해도 version 충돌이 발생할 수 있다.

예를 들어 재고가 충분하고 두 주문의 예약 수량을 모두 수용할 수 있어도, 같은 `product_stock` row를 수정했다는 이유만으로 한쪽 트랜잭션이 실패하고 재시도해야 할 수 있다.

하지만 재고 예약에서 필요한 실패 판단은 "version이 바뀌었는가"가 아니라 "조건을 만족하는 튜플이 실제로 변경되었는가"다.

```text
reserved_quantity += quantity
```

Optimistic locking은 충돌 후 재시도를 통해 성공 가능성을 다시 확인하는 흐름이 되기 쉽다. 경합이 커질수록 이 재시도는 응답 시간을 늘리고, 실패를 사용자에게 돌려줘야 하는 시점을 늦춘다.

따라서 이 설계에서는 version 충돌을 중심으로 판단하지 않고, 조건부 update의 affected row 수를 기준으로 성공과 실패를 판단한다. affected row가 0이면 변경된 튜플이 없다는 뜻이므로 현재 요청 안에서 재시도 루프를 반복하지 않고 현재 트랜잭션을 rollback한다.

---

## 5. Pessimistic Locking을 선택하지 않은 이유

Pessimistic locking은 같은 재고 row에 접근하는 트랜잭션을 순차 처리하게 만든다.

이 방식은 직관적이지만, 재고 변경 전체를 필요 이상으로 직렬화할 수 있다. 특히 예약 수량 증가처럼 변경 시점의 조건만 확인하면 되는 연산까지 명시적인 lock 범위에 넣으면, 실패 여부를 확인하기 전부터 응답 시간이 늘어난다.

이번 설계에서 필요한 것은 "항상 한 번에 하나의 트랜잭션만 재고를 만질 수 있게 하는 것"이 아니다.

필요한 것은 다음 조건이다.

```text
stock_quantity - reserved_quantity >= requested_quantity
```

이 조건을 만족하는 경우에만 예약 수량이 증가해야 한다.

조건부 atomic update는 이 조건 검증과 변경을 하나의 SQL 문으로 처리한다. 따라서 애플리케이션이 재고 row를 먼저 점유한 뒤 판단하지 않아도 되고, affected row 수로 실패 여부를 즉시 판단할 수 있다.

---

## 6. 예약 생성

예약 생성 시에는 실제 재고를 차감하지 않는다. 대신 `reserved_quantity`를 증가시킨다.

```text
available_quantity = stock_quantity - reserved_quantity
```

처리 순서는 다음과 같다.

1. 상품별 `product_stock.reserved_quantity`를 조건부 atomic update로 증가시킨다.
2. 모든 상품의 affected row 수가 1인지 확인한다.
3. 모두 성공하면 `stock_reservations`를 `IN_PROGRESS` 상태로 생성한다.
4. 하나라도 실패하면 전체 트랜잭션을 rollback한다.

이 구조에서는 동시에 여러 주문이 들어와도, 가용 재고 조건을 만족하는 예약만 성공한다. 조건을 만족하지 못하는 요청은 affected row가 0이 되므로 overselling으로 이어지지 않는다.

---

## 7. 예약 확정

결제 완료 후에는 예약 재고를 실제 판매 재고 차감으로 확정한다.

```sql
UPDATE product_stock
   SET stock_quantity = stock_quantity - :quantity,
       reserved_quantity = reserved_quantity - :quantity
 WHERE product_id = :productId
   AND deleted_at IS NULL
   AND stock_quantity >= :quantity
   AND reserved_quantity >= :quantity;
```

처리 순서는 다음과 같다.

1. `IN_PROGRESS` 예약 목록을 조회한다.
2. `productId`별 수량 합계를 계산한다.
3. `product_stock` 확정 atomic update를 실행한다.
4. 모든 상품의 affected row 수가 1인지 확인한다.
5. `stock_reservations`를 `IN_PROGRESS`에서 `COMPLETED`로 변경한다.
6. 변경된 예약 row 수가 expected reservation count와 같은지 확인한다.
7. 실패 시 전체 트랜잭션을 rollback한다.

여기서도 핵심은 상태 변경을 감으로 믿지 않는 것이다. 실제로 변경되어야 할 row가 모두 변경되었는지를 affected row 수로 검증한다.

---

## 8. 예약 만료와 취소

예약 만료와 결제 전 사용자 취소는 모두 예약 재고를 반환하는 흐름이다.

처리 결과는 다르지만, 재고 관점에서는 `reserved_quantity`를 감소시키는 연산이다.

```text
예약 만료:
order.status = EXPIRED
reservation.status = EXPIRED
payment.status = EXPIRED

결제 전 취소:
order.status = CANCELED
reservation.status = CANCELED
payment.status = CANCELED
```

두 흐름 모두 다음 기준을 따른다.

1. `IN_PROGRESS` 예약 목록을 조회한다.
2. 예약 ID와 `productId`별 수량 합계를 계산한다.
3. 예약 상태를 조건부 update로 변경한다.
4. affected row 수가 expected reservation count와 같은지 확인한다.
5. `product_stock.reserved_quantity`를 조건부 atomic update로 감소시킨다.
6. 모든 상품의 affected row 수가 1인지 확인한다.
7. 주문과 결제 상태를 변경한다.
8. 실패 시 전체 트랜잭션을 rollback한다.

예약 만료는 `PAYMENT_PENDING` 주문만 대상으로 한다. `FAILED` 주문은 PG 결제 승인 성공 가능성이 있는 상태이므로 예약 만료 대상에서 제외한다.

---

## 9. 결제 후 취소와 재고 복구

결제 후 취소는 예약 재고 반환이 아니라 실제 재고 복구다.

이미 결제가 완료되어 예약이 `COMPLETED` 되었기 때문에, 취소 성공 이후에는 `stock_quantity`를 증가시켜야 한다.

처리 순서는 다음과 같다.

1. 취소 요청 검증 트랜잭션에서 취소 가능한 주문과 결제인지 확인한다.
2. PG cancel은 DB 트랜잭션 밖에서 실행한다.
3. PG cancel 성공 이후 DB 트랜잭션에서 재고를 복구한다.
4. `product_stock.stock_quantity`를 atomic update로 증가시킨다.
5. `stock_reservations`를 `COMPLETED`에서 `CANCELED`로 변경한다.
6. 주문과 결제 상태를 `CANCELED`로 변경한다.

PG cancel 성공 후 DB 복구에 실패하면 다음 상태로 남긴다.

```text
order.status = FAILED
payment.status = COMPLETION_FAILED
```

이 상태는 수동 처리나 재시도 대상이다. 자동으로 재고를 어설프게 맞추는 대신, 실패 상태를 명시적으로 남겨 복구 흐름으로 넘긴다.

---

## 10. Alternatives Considered

| 옵션 | Pros | Cons |
| --- | --- | --- |
| Pessimistic Lock | 한 번에 하나의 트랜잭션만 재고 row를 변경하므로 이해하기 쉽다. | 별도 조회와 판단 구간까지 row를 점유해 응답 시간이 늘어날 수 있다. |
| Optimistic Lock | 명시적인 lock 대기 없이 version 기반으로 충돌을 감지할 수 있다. | version 충돌 후 재시도 루프가 반복되면 실패 응답이 늦어질 수 있다. |
| **선택: 조건부 Atomic Update** | affected row 수로 변경된 튜플이 없는 실패를 즉시 판단할 수 있다. 현재 요청 안에서 과한 재시도 없이 현재 트랜잭션을 rollback한다. | 모든 경로에서 affected row 수 검증과 rollback 기준을 일관되게 지켜야 한다. |

**선택 근거:**

이번 설계의 핵심은 재고 변경을 "row를 읽고 애플리케이션에서 판단한 뒤 다시 저장하는 작업"으로 보지 않는 것이다.

재고 예약과 반환은 조건을 만족하는 경우에만 반영되어야 한다. 따라서 조건부 atomic update를 사용해 DB 안에서 조건 검증과 변경을 함께 수행하고, 결과 row 수로 성공과 실패를 판단한다.

이 방식은 다음 요구를 동시에 만족한다.

- 가용 재고보다 많은 예약이 생성되지 않는다.
- 결제 완료 후 예약 재고가 실제 재고 차감으로 확정된다.
- 예약 만료와 취소 시 재고가 명시적으로 반환된다.
- 변경 대상 row 수가 기대와 다르면 rollback한다.
- 실패 시 변경된 튜플이 없다는 사실을 즉시 확인한다.
- 락 경합 상황에서도 현재 요청 안에서 과한 재시도 때문에 응답 시간을 늘리지 않는다.

---

## 11. Cross-cutting Concerns

### 11.1 Consistency

재고 변경과 예약 상태 변경은 같은 DB 트랜잭션 안에서 처리한다.

예를 들어 예약 확정에서는 `product_stock` 확정 update와 `stock_reservations` 상태 변경이 함께 성공해야 한다. 둘 중 하나라도 기대한 row 수만큼 변경되지 않으면 전체 트랜잭션을 rollback한다.

### 11.2 Concurrency

동시성 제어의 기준은 lock 획득 여부가 아니라 조건부 update 성공 여부다. 성공 여부는 affected row 수, 즉 실제로 변경된 튜플 수로 판단한다.

- 예약 생성: 가용 재고가 충분한 경우에만 `reserved_quantity` 증가
- 예약 확정: 실제 재고와 예약 재고가 충분한 경우에만 확정
- 예약 만료/취소: 기대한 예약 row가 모두 상태 변경된 경우에만 재고 반환
- 결제 후 취소: 완료된 예약을 취소 상태로 바꾸고 실제 재고 복구

각 단계는 affected row 수를 검증해 동시성 충돌을 감지한다. 기대한 row가 변경되지 않았다면 현재 요청 안에서 재시도 루프를 반복하지 않고 현재 트랜잭션을 rollback한다.

### 11.3 Failure Handling

실패는 조용히 무시하지 않는다.

변경되어야 하는 row가 변경되지 않았거나, expected affected row 수와 실제 affected row 수가 다르면 동시성 충돌로 본다. 이 경우 변경된 튜플이 없거나 기대와 다르다는 사실이 이미 확인되었으므로 해당 DB 트랜잭션은 rollback한다.

결제 후 취소처럼 PG cancel은 성공했지만 DB 복구에 실패할 수 있는 경우에는 `FAILED`와 `COMPLETION_FAILED` 상태를 남겨 재시도 또는 수동 처리 대상으로 전환한다.

### 11.4 Observability

재고 변경 실패는 단순한 validation error가 아니라 overselling 방지와 직접 연결된 동시성 충돌이다.

따라서 실패 로그에는 최소한 다음 정보가 필요하다.

| 항목 | 이유 |
| --- | --- |
| orderId | 어떤 주문에서 실패했는지 확인 |
| reservationIds | 어떤 예약 row가 대상이었는지 확인 |
| productId별 quantity | 어떤 상품 수량 변경이 실패했는지 확인 |
| failure reason | 조건 불만족인지 row count 불일치인지 확인 |
| retryCount | 실패 후 과한 재시도가 발생하지 않았는지 확인 |

---

## 12. 결론

이 설계는 재고 정합성의 핵심 문제를 overselling 방지로 본다.

사용자가 결제했는데 상품을 받을 수 없는 상황을 막기 위해, 주문 생성 시점에 예약 재고를 확보하고 결제 완료 시점에 실제 재고 차감으로 확정한다.

이를 위해 `product_stock`은 실제 재고인 `stock_quantity`와 예약 재고인 `reserved_quantity`를 함께 가진다. 가용 재고는 `stock_quantity - reserved_quantity`로 계산한다.

모든 재고 변경은 조건부 atomic update로 처리한다.

- 예약 생성: `reserved_quantity` 증가
- 예약 확정: `stock_quantity` 감소, `reserved_quantity` 감소
- 예약 만료: `reserved_quantity` 반환
- 결제 전 취소: `reserved_quantity` 반환
- 결제 후 취소: `stock_quantity` 복구

이 방식은 optimistic locking처럼 현재 요청 안에서 version 충돌 재시도 루프를 반복하지 않고, pessimistic locking처럼 별도 조회와 판단 구간까지 재고 row를 점유하지 않는다.

대신 DB가 조건 검증과 변경을 하나의 atomic update로 수행하게 하고, 애플리케이션은 affected row 수를 검증해 변경된 튜플이 없는 실패를 즉시 판단한다.

---

### Reference

- `docs/order/design_v2.md`
- `product_stock.stock_quantity`: 실제 판매 재고
- `product_stock.reserved_quantity`: 결제 전 확보한 예약 재고
- `stock_reservations`: 주문별 예약 재고 상태
- `StockReservationStatus.IN_PROGRESS`
- `StockReservationStatus.COMPLETED`
- `StockReservationStatus.EXPIRED`
- `StockReservationStatus.CANCELED`
