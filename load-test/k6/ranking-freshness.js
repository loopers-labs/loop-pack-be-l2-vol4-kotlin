// 신선도 프로브 — 이벤트 발생 → 랭킹 점수 반영까지 지연(ms) 측정. S-WRITE 배경 위에서 돌린다.
//   신호 = 센티널 상품 상세 조회 VIEWS_PER_PROBE회 (ProductViewedEvent × N → 오늘판 +0.1×N)
//   ⚠️ like 엔드포인트가 현재 브랜치에 없어 view 를 신호로 사용. view 발행은 유실 허용 경로(@Async 직접 발행)라
//      POLL_MAX 초과 시 lost 로 집계하고 다음 회차로 넘어간다 (유실률도 관측 대상).
//   전제: 센티널 상품이 오늘판 page1(top SIZE) 안에 있도록 시드 (점수 최상위로 시딩)
//   응답 파싱: RankingResponse.items[].{productId, score} 가정 — 필드명 다르면 extractScore() 만 수정
// 실행: k6 run -e BASE_URL=http://<app>:8080 -e SENTINEL_ID=900001 load-test/k6/ranking-freshness.js
import http from 'k6/http';
import { sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SENTINEL = parseInt(__ENV.SENTINEL_ID || '900001');
const REPEATS = parseInt(__ENV.REPEATS || '30');
const POLL_MS = parseInt(__ENV.POLL_MS || '500');
const POLL_MAX = parseInt(__ENV.POLL_MAX || '240'); // 500ms × 240 = 최대 120s 대기
const VIEWS_PER_PROBE = parseInt(__ENV.VIEWS_PER_PROBE || '5');
const SIZE = parseInt(__ENV.SIZE || '20');
const TIMEOUT = __ENV.TIMEOUT || '10s';

const freshMs = new Trend('ranking_freshness_ms', true);
const probeLost = new Counter('ranking_probe_lost');

export const options = {
    scenarios: {
        probe: { executor: 'per-vu-iterations', vus: 1, iterations: REPEATS, maxDuration: '40m' },
    },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'max'],
};

function kstToday() {
    const d = new Date(Date.now() + 9 * 3600 * 1000);
    return d.toISOString().slice(0, 10).replace(/-/g, '');
}

function extractScore() {
    const res = http.get(
        `${BASE}/api/v1/rankings?date=${kstToday()}&size=${SIZE}&page=1`,
        { timeout: TIMEOUT, tags: { api: 'rankings_probe' } },
    );
    if (res.status !== 200) return null;
    let body;
    try { body = res.json(); } catch (e) { return null; }
    const items = (body.data && body.data.items) || body.items || [];
    const hit = items.find((it) => it.productId === SENTINEL);
    return hit ? Number(hit.score) : null;
}

export default function () {
    const before = extractScore();
    if (before === null) { probeLost.add(1); sleep(2); return; }

    for (let i = 0; i < VIEWS_PER_PROBE; i++) {
        http.get(`${BASE}/api/v1/products/${SENTINEL}`, { timeout: TIMEOUT, tags: { api: 'probe_view' } });
    }
    const t0 = Date.now();

    for (let p = 0; p < POLL_MAX; p++) {
        sleep(POLL_MS / 1000);
        const now = extractScore();
        if (now !== null && now > before) {
            freshMs.add(Date.now() - t0);
            sleep(2);
            return;
        }
    }
    probeLost.add(1);
    sleep(2);
}
