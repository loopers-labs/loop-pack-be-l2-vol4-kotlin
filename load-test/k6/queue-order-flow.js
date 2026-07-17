// M2 — 대기열 여정 부하: enter → position 폴링(nextPollSeconds 준수) → ADMITTED → 작성 think time → 주문
//   유저 지표: queue_wait_time(진입→입장), wait_promise_error(실제−예상), journey 성공률, API 태그 분해
//   암 선택 (MODE):
//     spike : SPIKE_USERS(기본 8000)명을 10초에 진입 (블랙프라이데이 오픈런) — 도착 800/s = 4×C
//     ramp  : M1과 동일 램프 100→800/s ×4분 (같은 부하, 두 세계 비교)
//   ABANDON_RATE: 대기 중 잠적 비율 (폴링 중단, 줄엔 남음 → 토큰 미사용 만료 = 유량 손실 측정)
// 실행:
//   k6 run -e MODE=spike -e SPIKE_USERS=8000 -e BASE_URL=http://<app>:8080 load-test/k6/queue-order-flow.js
//   k6 run -e MODE=ramp -e BASE_URL=http://<app>:8080 load-test/k6/queue-order-flow.js
import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const MODE = __ENV.MODE || 'spike';
const SPIKE_USERS = parseInt(__ENV.SPIKE_USERS || '8000');
const ABANDON_RATE = parseFloat(__ENV.ABANDON_RATE || '0');
const THINK_MIN = parseInt(__ENV.THINK_MIN || '30');
const THINK_MAX = parseInt(__ENV.THINK_MAX || '120');
// POLL_MODE: 숫자(초) = 프론트 고정 간격 폴링(기본 3초) / 'server' = 응답의 nextPollSeconds 준수
const POLL_MODE = __ENV.POLL_MODE || '3';
const MAX_WAIT_SECONDS = parseInt(__ENV.MAX_WAIT_SECONDS || '420');
const HOT_PRODUCT = parseInt(__ENV.HOT_PRODUCT || '900001');
const PRICE = 10000;

const queueWaitTime = new Trend('queue_wait_time', true);
const waitPromiseError = new Trend('wait_promise_error', true);
const journeySuccess = new Rate('journey_success');
const abandoned = new Counter('abandoned_users');

const spikeScenario = {
    executor: 'ramping-arrival-rate',
    startRate: 0,
    timeUnit: '1s',
    preAllocatedVUs: SPIKE_USERS + 200,
    maxVUs: SPIKE_USERS + 500,
    gracefulStop: '30s',
    stages: [
        { target: Math.round(SPIKE_USERS / 10), duration: '2s' },
        { target: Math.round(SPIKE_USERS / 10), duration: '8s' },
        { target: 0, duration: '1s' },
        // 진입은 위 11초로 끝나지만, 시나리오 창을 여정 최장 길이(드레인+작성)만큼 열어둬야
        // 대기·작성 중인 iteration이 잘리지 않는다 (executor는 stages 종료 시 in-flight를 중단시킴)
        { target: 0, duration: __ENV.DRAIN_WINDOW || '240s' },
    ],
};

const rampScenario = {
    executor: 'ramping-arrival-rate',
    startRate: 100,
    timeUnit: '1s',
    preAllocatedVUs: 3000,
    maxVUs: 12000,
    gracefulStop: '30s',
    stages: [
        { target: 100, duration: '1m' },
        { target: 200, duration: '1m' },
        { target: 400, duration: '1m' },
        { target: 800, duration: '1m' },
    ],
};

export const options = {
    scenarios: { journey: MODE === 'spike' ? spikeScenario : rampScenario },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const AUTH = (user) => ({
    'Content-Type': 'application/json',
    'X-Loopers-LoginId': user,
    'X-Loopers-LoginPw': 'Perf@Loop2026',
});

// 대기열은 userId 기반(ZADD NX·토큰 키)이라 여정마다 고유 계정 필수 — 계정 공유 시 줄·토큰이 겹쳐 여정이 오염된다.
// perf1~perf{USER_POOL}은 perf-seed-l5 계정에 자격증명을 부여해 사용 (스파이크 인원 ≤ USER_POOL 유지).
const USER_POOL = parseInt(__ENV.USER_POOL || '20000');

export default function () {
    const user = `perf${(exec.scenario.iterationInTest % USER_POOL) + 1}`;
    const headers = AUTH(user);
    const enteredAt = Date.now();

    const enterRes = http.post(`${BASE}/api/v1/queue/enter`, null, {
        headers, timeout: '10s', tags: { api: 'queue_enter' },
    });
    check(enterRes, { 'enter 2xx': (r) => r.status === 200 });
    if (enterRes.status !== 200) { journeySuccess.add(false); return; }

    let body;
    try { body = enterRes.json('data'); } catch (e) { journeySuccess.add(false); return; }
    const promisedWait = body.estimatedWaitSeconds;
    let token = body.status === 'ADMITTED' ? body.token : null;

    if (!token && Math.random() < ABANDON_RATE) { abandoned.add(1); return; }

    while (!token) {
        if ((Date.now() - enteredAt) / 1000 > MAX_WAIT_SECONDS) { journeySuccess.add(false); return; }
        sleep(POLL_MODE === 'server'
            ? Math.min(Math.max(body.nextPollSeconds || 1, 1), 10)
            : parseFloat(POLL_MODE));
        const posRes = http.get(`${BASE}/api/v1/queue/position`, {
            headers, timeout: '10s', tags: { api: 'queue_position' },
        });
        check(posRes, { 'position 2xx': (r) => r.status === 200 });
        if (posRes.status !== 200) { journeySuccess.add(false); return; }
        try { body = posRes.json('data'); } catch (e) { journeySuccess.add(false); return; }
        if (body.status === 'ADMITTED') token = body.token;
        if (body.status === 'NOT_IN_QUEUE') { journeySuccess.add(false); return; }
    }

    const actualWait = (Date.now() - enteredAt) / 1000;
    queueWaitTime.add(actualWait * 1000);
    if (promisedWait != null) waitPromiseError.add((actualWait - promisedWait) * 1000);

    sleep(THINK_MIN + Math.random() * (THINK_MAX - THINK_MIN));

    const orderRes = http.post(`${BASE}/api/v1/orders`, JSON.stringify({
        items: [{ productId: HOT_PRODUCT, quantity: 1, price: PRICE }],
        couponId: null,
        expectedOriginalAmount: PRICE,
        expectedDiscountAmount: 0,
    }), { headers, timeout: '10s', tags: { api: 'orders' } });

    const ok = check(orderRes, { 'order 200': (r) => r.status === 200 });
    journeySuccess.add(ok);
}
