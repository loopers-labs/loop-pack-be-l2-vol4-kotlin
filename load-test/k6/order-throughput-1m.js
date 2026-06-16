// 1분 주문 처리량 측정 (live 프로젝트 방식 축소판)
//   ARM=hot    : 단일 인기 상품(1001)에 집중 — 재고 행 락 직렬화 천장 측정
//   ARM=spread : 상품 10개(1011~1020) 분산 — 경합 없는 대조군
// 실행: k6 run -e ARM=hot -e RATE=50 -e DURATION=1m load-test/k6/order-throughput-1m.js
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = parseInt(__ENV.RATE || '50');
const DURATION = __ENV.DURATION || '1m';
const ARM = __ENV.ARM || 'hot';
const HOT_PRODUCT = 1001;
const SPREAD_PRODUCTS = [1011, 1012, 1013, 1014, 1015, 1016, 1017, 1018, 1019, 1020];

export const options = {
    scenarios: {
        orders: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: 100,
            maxVUs: 300,
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const user = `perfuser${Math.floor(Math.random() * 20) + 1}`;
    const productId = ARM === 'hot'
        ? HOT_PRODUCT
        : SPREAD_PRODUCTS[Math.floor(Math.random() * SPREAD_PRODUCTS.length)];

    const res = http.post(`${BASE}/api/v1/orders`, JSON.stringify({
        items: [{ productId: productId, quantity: 1 }],
        couponId: null,
        expectedOriginalAmount: 10000,
        expectedDiscountAmount: 0,
        expectedTotalAmount: 10000,
    }), {
        headers: {
            'Content-Type': 'application/json',
            'X-Loopers-LoginId': user,
            'X-Loopers-LoginPw': 'Perf@Loop2026',
        },
    });

    check(res, { 'status 200': (r) => r.status === 200 });
}
