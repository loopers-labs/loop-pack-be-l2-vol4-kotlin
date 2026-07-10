// Week5 L5 상품 목록 조회 부하 — 인덱스 전/후 비교용
//
// 주 시나리오: GET /api/v1/products?sort=LIKES_DESC (좋아요순 첫 페이지)
//   baseline(인덱스 X): 매 요청 100k 행 풀스캔 + filesort
//   indexing(인덱스 O): 복합 인덱스 순서 읽기, 정렬 제거
//   70% 글로벌 / 30% 브랜드 필터(idx_p_brand_lc_id 자극)
//
// 실행:
//   본측정:  k6 run -e MODE=measure -e RATE=1000 -e DURATION=3m -e BASE=http://175.208.203.10:8080 \
//               --summary-export=results/week5-l5/runs/<id>.json product-list-load.js
//   웜업:    k6 run -e MODE=warmup  -e PEAK=1000 -e BASE=http://... product-list-load.js
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://175.208.203.10:8080';
const MODE = __ENV.MODE || 'measure';
const RATE = parseInt(__ENV.RATE || '1000');
const PEAK = parseInt(__ENV.PEAK || '1000');
const DURATION = __ENV.DURATION || '3m';
const SIZE = parseInt(__ENV.SIZE || '20');
const BRAND_RATIO = parseFloat(__ENV.BRAND_RATIO || '0.3'); // 0 = 글로벌 첫 페이지 100%(캐시 키 집중)

// 3000 TPS × 느린 baseline 응답까지 흡수하도록 VU 여유 확보
const PRE_VUS = parseInt(__ENV.PRE_VUS || '600');
const MAX_VUS = parseInt(__ENV.MAX_VUS || '3000');

// 포화 시 무한 큐잉 방지: 프론트 표준 타임아웃(10s, axios 관례 + Nielsen 10초 주의 한계) 초과 = 실패.
// 요청이 10s 안에 종결되므로 런도 자연히 duration+~10s 로 bounded.
const TIMEOUT = __ENV.TIMEOUT || '10s';

const measureScenario = {
    executor: 'constant-arrival-rate',
    rate: RATE,
    timeUnit: '1s',
    duration: DURATION,
    preAllocatedVUs: PRE_VUS,
    maxVUs: MAX_VUS,
    gracefulStop: '10s',   // 종료 시 in-flight 대기 상한 → 포화 런도 ~3분에 마감
};

// 웜업: 1분간 0→PEAK 로 선형 증가하며 JIT 컴파일 유도
const warmupScenario = {
    executor: 'ramping-arrival-rate',
    startRate: 0,
    timeUnit: '1s',
    preAllocatedVUs: PRE_VUS,
    maxVUs: MAX_VUS,
    gracefulStop: '10s',
    stages: [
        { target: PEAK, duration: '1m' },
    ],
};

export const options = {
    scenarios: { load: MODE === 'warmup' ? warmupScenario : measureScenario },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<3000'],
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
    let url;
    if (Math.random() < BRAND_RATIO) {
        // 브랜드 필터 (상품 충분한 브랜드 1..100에서 랜덤)
        const brandId = Math.floor(Math.random() * 100) + 1;
        url = `${BASE}/api/v1/products?sort=LIKES_DESC&brandId=${brandId}&size=${SIZE}`;
    } else {
        url = `${BASE}/api/v1/products?sort=LIKES_DESC&size=${SIZE}`;
    }
    const res = http.get(url, { timeout: TIMEOUT });
    check(res, { 'status 200': (r) => r.status === 200 });
}
