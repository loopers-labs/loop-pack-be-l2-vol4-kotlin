// EXP-04 M1 — 주문 API 맨몸 천장 측정 (대기열 없는 커밋)
//   ramping-arrival-rate 100→200→400→800/s, 단계별 1분 (open model: 응답 지연과 무관하게 도착 유지)
//   ARM=hot    : 단일 인기 상품(900001) 집중 — 재고 행 락 직렬화 천장
//   ARM=spread : 상품 10개(900011~900020) 분산 — 경합 없는 대조군
// 요청 본문은 현 DTO 계약(품목별 price 필수, expectedTotalAmount 없음) 기준.
// 실행: k6 run -e ARM=hot --summary-export=load-test/results/exp04/hot.summary.json load-test/k6/order-ramp.js
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const ARM = __ENV.ARM || 'hot';
const HOT_PRODUCT = parseInt(__ENV.HOT_PRODUCT || '900001');
const SPREAD_BASE = parseInt(__ENV.SPREAD_BASE || '900010'); // +1 ~ +10
const PRICE = 10000;

const MODE = __ENV.MODE || 'measure';
const SCALE = parseFloat(__ENV.SCALE || '1'); // 혼합 런: 총 도착률에서 이 암이 차지하는 비율 (hot 0.7 + spread 0.3 두 프로세스 동시 실행)

const rampScenario = {
    executor: 'ramping-arrival-rate',
    startRate: Math.round(100 * SCALE),
    timeUnit: '1s',
    preAllocatedVUs: Math.round(300 * SCALE) || 50,
    maxVUs: Math.round(2000 * SCALE) || 300,
    gracefulStop: '10s',
    stages: [
        { target: Math.round(100 * SCALE), duration: '1m' },
        { target: Math.round(200 * SCALE), duration: '1m' },
        { target: Math.round(400 * SCALE), duration: '1m' },
        { target: Math.round(800 * SCALE), duration: '1m' },
    ],
};

const warmupScenario = {
    executor: 'constant-arrival-rate',
    rate: 50,
    timeUnit: '1s',
    duration: '1m',
    preAllocatedVUs: 100,
    maxVUs: 300,
    gracefulStop: '10s',
};

export const options = {
    scenarios: { orders: MODE === 'warmup' ? warmupScenario : rampScenario },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
    const user = `perfuser${Math.floor(Math.random() * 20) + 1}`;
    const productId = ARM === 'hot'
        ? HOT_PRODUCT
        : SPREAD_BASE + Math.floor(Math.random() * 10) + 1;

    const res = http.post(`${BASE}/api/v1/orders`, JSON.stringify({
        items: [{ productId: productId, quantity: 1, price: PRICE }],
        couponId: null,
        expectedOriginalAmount: PRICE,
        expectedDiscountAmount: 0,
    }), {
        headers: {
            'Content-Type': 'application/json',
            'X-Loopers-LoginId': user,
            'X-Loopers-LoginPw': 'Perf@Loop2026',
        },
        timeout: '10s',
        tags: { api: 'orders' },
    });

    check(res, { 'status 200': (r) => r.status === 200 });
}
