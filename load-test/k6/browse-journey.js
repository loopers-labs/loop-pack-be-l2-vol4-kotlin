// 탐색 유저 여정 배경 부하 — 목록 → (페이지네이션) → 상세 1~N개
//   유저 1명의 여정: 목록 진입(70% 글로벌 첫 페이지=캐시 / 30% 브랜드 필터=MySQL)
//     → 30% 확률로 다음 페이지 1~2회(커서) → 목록에서 본 상품 중 1~3개 상세 조회(think time 포함)
//   상세 조회는 상품+브랜드 2쿼리(MySQL) + ProductViewedEvent 카프카 발행까지 실림 (실제 부하 모양)
//   RATE = "초당 탐색 유저 도착 수" (요청 수 아님). 유저당 평균 ~3.4 요청 (목록 ~1.4 + 상세 ~2)
// 실행: k6 run -e RATE=100 -e DURATION=4m10s -e BASE_URL=http://localhost:8080 load-test/k6/browse-journey.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = parseInt(__ENV.RATE || '100');
const DURATION = __ENV.DURATION || '4m10s';
const BRAND_RATIO = parseFloat(__ENV.BRAND_RATIO || '0.3');
const PAGE_MORE_RATIO = parseFloat(__ENV.PAGE_MORE_RATIO || '0.3');
const DETAILS_MAX = parseInt(__ENV.DETAILS_MAX || '3');
const SIZE = parseInt(__ENV.SIZE || '20');
const TIMEOUT = __ENV.TIMEOUT || '10s';

export const options = {
    scenarios: {
        browse: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: parseInt(__ENV.PRE_VUS || '500'),
            maxVUs: parseInt(__ENV.MAX_VUS || '2000'),
            gracefulStop: '10s',
        },
    },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function listUrl(cursor, brandId) {
    let u = `${BASE}/api/v1/products?sort=LIKES_DESC&size=${SIZE}`;
    if (brandId) u += `&brandId=${brandId}`;
    if (cursor) u += `&cursor=${encodeURIComponent(cursor)}`;
    return u;
}

export default function () {
    const brandId = Math.random() < BRAND_RATIO ? Math.floor(Math.random() * 100) + 1 : null;
    const ids = [];
    let cursor = null;
    const pages = 1 + (Math.random() < PAGE_MORE_RATIO ? (Math.random() < 0.5 ? 1 : 2) : 0);

    for (let p = 0; p < pages; p++) {
        const res = http.get(listUrl(cursor, brandId), { timeout: TIMEOUT, tags: { api: 'products_list' } });
        check(res, { 'list 200': (r) => r.status === 200 });
        if (res.status !== 200) return;
        let data;
        try { data = res.json('data'); } catch (e) { return; }
        (data.content || []).forEach((it) => ids.push(it.id));
        if (!data.hasNext || !data.nextCursor) break;
        cursor = data.nextCursor;
        sleep(0.3 + Math.random() * 0.7);
    }

    const views = 1 + Math.floor(Math.random() * DETAILS_MAX);
    for (let v = 0; v < views && ids.length > 0; v++) {
        const id = ids[Math.floor(Math.random() * ids.length)];
        const res = http.get(`${BASE}/api/v1/products/${id}`, { timeout: TIMEOUT, tags: { api: 'products_detail' } });
        check(res, { 'detail 200': (r) => r.status === 200 });
        sleep(0.3 + Math.random() * 1.2);
    }
}
