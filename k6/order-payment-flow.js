import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// =====================================================================
// 주문 생성 → 결제 요청 부하테스트
// ---------------------------------------------------------------------
// 흐름:
//   1) POST /api/v1/orders   (주문 생성, CREATED)        ← K6 측정
//   2) POST /api/v1/payments (결제 요청 → PG 동기 호출)  ← K6 측정, 응답은 PENDING
//   3) PG 시뮬레이터(8082)가 비동기로 /payments/callback 호출 → PAYMENT_COMPLETED
//        └ 콜백은 K6가 호출하지 않는다(실제 PG 자동 콜백 구조).
//          VERIFY_CALLBACK=true 면 주문을 폴링해 완료 전이를 검증한다(처리량 왜곡되니 검증용).
//
// 실행 예:
//   k6 run -e BRAND_ID=12 k6/order-payment-flow.js
//   k6 run -e BRAND_ID=12 -e USER_COUNT=200 -e VERIFY_CALLBACK=true k6/order-payment-flow.js
// =====================================================================

const BASE_URL       = __ENV.BASE_URL || 'http://localhost:8080';
const BRAND_ID       = __ENV.BRAND_ID;                       // seed.sql 출력값 (필수)
const USER_COUNT     = parseInt(__ENV.USER_COUNT || '200');  // seed.sql 의 @USER_COUNT 와 일치
const PASSWORD       = __ENV.PASSWORD || 'password1234';
const VERIFY_CALLBACK = (__ENV.VERIFY_CALLBACK || 'false') === 'true';

const CARD_TYPES = ['SAMSUNG', 'KB', 'HYUNDAI'];

// ---- 커스텀 메트릭 ----------------------------------------------------
const orderDuration    = new Trend('biz_order_duration', true);
const paymentDuration  = new Trend('biz_payment_duration', true);
const orderSuccess     = new Rate('biz_order_success');
const paymentAccepted  = new Rate('biz_payment_accepted');     // PG 접수(PENDING) 성공률
const callbackComplete = new Rate('biz_callback_completed');   // 콜백까지 완료(검증 모드)
const flowErrors       = new Counter('biz_flow_errors');

// ---- 부하 프로파일 ----------------------------------------------------
export const options = {
  scenarios: {
    order_payment: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },   // 워밍업
        { duration: '1m',  target: 50 },   // 정상 부하
        { duration: '1m',  target: 100 },  // 피크
        { duration: '30s', target: 0 },    // 쿨다운
      ],
      gracefulStop: '30s',
    },
  },
  thresholds: {
    http_req_failed:        ['rate<0.01'],
    'biz_order_duration':   ['p(95)<500'],
    'biz_payment_duration': ['p(95)<800'],   // PG 동기 호출 100~500ms 포함
    'biz_order_success':    ['rate>0.99'],
    'biz_payment_accepted': ['rate>0.99'],
  },
};

// ---- setup: 상품 ID 목록 확보 ----------------------------------------
export function setup() {
  if (!BRAND_ID) {
    throw new Error('BRAND_ID 가 필요합니다. seed.sql 실행 후 출력된 brand_id 를 -e BRAND_ID=... 로 넘기세요.');
  }
  const res = http.get(`${BASE_URL}/api/v1/products?brandId=${BRAND_ID}&size=200`);
  const body = res.json();
  if (res.status !== 200 || !body || !body.data || !body.data.items.length) {
    throw new Error(`상품 목록 조회 실패 (status=${res.status}). 시드 데이터/서버를 확인하세요.`);
  }
  const productIds = body.data.items.map((it) => it.id);
  console.log(`[setup] brandId=${BRAND_ID}, products=${productIds.length}, users=${USER_COUNT}`);
  return { productIds };
}

function authHeaders(userNo) {
  const loginId = 'loadtest' + String(userNo).padStart(4, '0');
  return {
    'Content-Type': 'application/json',
    'X-Loopers-LoginId': loginId,
    'X-Loopers-LoginPw': PASSWORD,
  };
}

// ---- VU 본문 ----------------------------------------------------------
export default function (data) {
  const userNo    = randomIntBetween(1, USER_COUNT);
  const productId = data.productIds[randomIntBetween(0, data.productIds.length - 1)];
  const headers   = authHeaders(userNo);

  let orderId = null;

  group('1. 주문 생성', () => {
    const payload = JSON.stringify({ items: [{ productId: productId, quantity: 1 }] });
    const res = http.post(`${BASE_URL}/api/v1/orders`, payload, { headers, tags: { name: 'createOrder' } });
    orderDuration.add(res.timings.duration);

    const body = res.json();
    const ok = check(res, {
      '주문 생성 200': (r) => r.status === 200,
      '주문 status=CREATED': () => body && body.data && body.data.status === 'CREATED',
    });
    orderSuccess.add(ok);
    if (ok) {
      orderId = body.data.id;
    } else {
      flowErrors.add(1);
      console.error(`주문 실패 status=${res.status} body=${res.body}`);
    }
  });

  if (orderId === null) {
    sleep(1);
    return;
  }

  group('2. 결제 요청', () => {
    const payload = JSON.stringify({
      orderId: String(orderId),
      cardType: CARD_TYPES[randomIntBetween(0, CARD_TYPES.length - 1)],
      cardNo: '1234-5678-9814-1451',
    });
    const res = http.post(`${BASE_URL}/api/v1/payments`, payload, { headers, tags: { name: 'requestPayment' } });
    paymentDuration.add(res.timings.duration);

    const body = res.json();
    const ok = check(res, {
      '결제 접수 200': (r) => r.status === 200,
      '결제 status=PENDING': () => body && body.data && body.data.status === 'PENDING',
    });
    paymentAccepted.add(ok);
    if (!ok) {
      flowErrors.add(1);
      console.error(`결제 실패 status=${res.status} body=${res.body}`);
    }
  });

  // 콜백은 PG 시뮬레이터가 비동기 처리. 검증 모드일 때만 폴링해 완료 전이를 확인한다.
  if (VERIFY_CALLBACK && orderId !== null) {
    group('3. 콜백 완료 검증(폴링)', () => {
      // 콜백 성공 → PAYMENT_COMPLETED. 콜백 실패는 no-op라 PAYMENT_PENDING 유지(=타임아웃 → false).
      // PG 시뮬레이터가 ~40% 확률로 실패하므로 완료율은 대략 60% 부근이 정상.
      let completed = false;
      for (let i = 0; i < 10; i++) {     // 최대 ~3초 폴링
        sleep(0.3);
        const res = http.get(`${BASE_URL}/api/v1/orders/${orderId}`, { headers, tags: { name: 'getOrder' } });
        const body = res.json();
        const status = body && body.data ? body.data.status : null;
        if (status === 'PAYMENT_COMPLETED') {
          completed = true;
          break;
        }
      }
      callbackComplete.add(completed);
    });
  }

  sleep(randomIntBetween(1, 2)); // think-time
}
