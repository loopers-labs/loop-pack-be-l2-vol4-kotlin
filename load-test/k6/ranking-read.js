// 랭킹 페이지 조회 램프 — R9 S-READ 암 (EXP-02/04/05 공용)
//   50→100→200→400/s 각 1분 (open model). 페이지 분포: page1 80% / page2~5 15% / deep(6~50) 5%
//   date: 오늘 90% / 어제 10% (KST 기준, ?date=yyyyMMdd)
//   판정은 사후 분석(threshold 없음) — k6 p95 ↔ actuator 갭으로 포화 판독 (R8 이중 관측)
// 실행: k6 run -e BASE_URL=http://<app>:8080 --summary-export=read.json load-test/k6/ranking-read.js
//   웜업: k6 run -e MODE=warmup ... (상수 20/s × 1분)
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const TIMEOUT = __ENV.TIMEOUT || '10s';
const MODE = __ENV.MODE || 'ramp';
const YESTERDAY_RATIO = parseFloat(__ENV.YESTERDAY_RATIO || '0.1');
const SIZE = parseInt(__ENV.SIZE || '20');

// KST(UTC+9) 날짜 yyyyMMdd — 부하기 로컬 TZ 무관
function kstDate(offsetDays) {
    const d = new Date(Date.now() + (9 * 3600 + offsetDays * 86400) * 1000);
    return d.toISOString().slice(0, 10).replace(/-/g, '');
}

const scenarios = MODE === 'warmup'
    ? {
        warmup: {
            executor: 'constant-arrival-rate',
            rate: 20, timeUnit: '1s', duration: '1m',
            preAllocatedVUs: 50, maxVUs: 200, gracefulStop: '10s',
        },
    }
    : {
        read: {
            executor: 'ramping-arrival-rate',
            startRate: 50, timeUnit: '1s',
            stages: [
                { target: 50, duration: '1m' },
                { target: 100, duration: '1m' },
                { target: 200, duration: '1m' },
                { target: 400, duration: '1m' },
            ],
            preAllocatedVUs: parseInt(__ENV.PRE_VUS || '300'),
            maxVUs: parseInt(__ENV.MAX_VUS || '2000'),
            gracefulStop: '10s',
        },
    };

export const options = {
    scenarios,
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function pickPage() {
    const r = Math.random();
    if (r < 0.80) return 1;
    if (r < 0.95) return 2 + Math.floor(Math.random() * 4);
    return 6 + Math.floor(Math.random() * 45);
}

export default function () {
    const date = Math.random() < YESTERDAY_RATIO ? kstDate(-1) : kstDate(0);
    const page = pickPage();
    const res = http.get(
        `${BASE}/api/v1/rankings?date=${date}&size=${SIZE}&page=${page}`,
        { timeout: TIMEOUT, tags: { api: 'rankings' } },
    );
    check(res, { 'rankings 200': (r) => r.status === 200 });
}
