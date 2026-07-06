// 비동기 발급 계약(EXP-03)용: POST 접수 → requestId → 1초 간격 GET polling(최대 10회)로 확정 확인.
// time_to_decision = 접수 시작부터 ISSUED/REJECTED 확정(또는 게이트 즉시 409)까지 — 클라이언트 관점.
// 10초 내 미확정은 coupon_undecided 로 집계 (확정 게이트 위반).
// 실행:
//   S1 스파이크: BASE_URL=http://127.0.0.1:8080 COUPON_ID=90001 k6 run load-test/k6/coupon-issue-accept-poll.js
//   S2 계단:     MODE=step STAGE_DUR=1m COUPON_ID=90002 k6 run load-test/k6/coupon-issue-accept-poll.js
// 시드: load-test/coupon-perf-seed.sql (cpuser00001~cpuser10000, 쿠폰 90001=한도100 / 90002=한도10만)
import http from "k6/http";
import exec from "k6/execution";
import { sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";
const COUPON_ID = Number(__ENV.COUPON_ID || 90001);
const PASSWORD = __ENV.PASSWORD || "Perf@Loop2026";
const MODE = __ENV.MODE || "spike";
const STAGE_DUR = __ENV.STAGE_DUR || "1m";
const POLL_MAX = Number(__ENV.POLL_MAX || 10); // 1초 간격 × 10회 = 확정 게이트 10초

// 200(접수/조회)·409(게이트 거절) 는 정상 결정. 그 외만 실패 집계.
http.setResponseCallback(http.expectedStatuses(200, 409));

const issued = new Counter("coupon_issued");
const rejectedAtGate = new Counter("coupon_rejected_at_gate");
const rejectedAtConsumer = new Counter("coupon_rejected_at_consumer");
const undecided = new Counter("coupon_undecided");
const acceptMs = new Trend("coupon_accept_ms", true);
const decisionMs = new Trend("coupon_decision_ms", true);
const unexpected = new Rate("coupon_unexpected");

const spikeScenario = {
  executor: "constant-arrival-rate",
  rate: 1000, // 1000/s × 10s = 10,000 요청
  timeUnit: "1s",
  duration: "10s",
  preAllocatedVUs: 500,
  maxVUs: 3000,
};

const stepScenario = {
  executor: "ramping-arrival-rate",
  startRate: 100,
  timeUnit: "1s",
  preAllocatedVUs: 500,
  maxVUs: 3000,
  stages: [
    { target: 100, duration: STAGE_DUR },
    { target: 200, duration: STAGE_DUR },
    { target: 400, duration: STAGE_DUR },
    { target: 800, duration: STAGE_DUR },
  ],
};

export const options = {
  scenarios: { load: MODE === "step" ? stepScenario : spikeScenario },
  thresholds: {
    coupon_unexpected: ["rate<0.01"], // 5xx/네트워크 오류 1% 미만
  },
};

function loginId() {
  // spike: iterationInTest 0..9999 → distinct 유저 / step: 유저 1만 순환 (재요청 = 게이트 중복 거절 경로)
  const n = (exec.scenario.iterationInTest % 10000) + 1;
  return "cpuser" + String(n).padStart(5, "0");
}

function authHeaders() {
  return {
    "Content-Type": "application/json",
    "X-Loopers-LoginId": loginId(),
    "X-Loopers-LoginPw": PASSWORD,
  };
}

export default function () {
  const startedAt = Date.now();
  const headers = authHeaders();
  const accept = http.post(
    `${BASE_URL}/api/v1/coupons/issue`,
    JSON.stringify({ couponId: COUPON_ID }),
    { headers },
  );
  acceptMs.add(accept.timings.duration);

  if (accept.status === 409) {
    // Redis 게이트 즉시 거절(SOLD_OUT/ALREADY_ISSUED) — 이것도 유효한 "결정"
    rejectedAtGate.add(1);
    decisionMs.add(Date.now() - startedAt);
    unexpected.add(false);
    return;
  }
  if (accept.status !== 200) {
    unexpected.add(true);
    return;
  }
  unexpected.add(false);

  const requestId = accept.json("data.requestId") || accept.json("requestId");
  if (!requestId) {
    unexpected.add(true);
    return;
  }

  for (let attempt = 0; attempt < POLL_MAX; attempt++) {
    sleep(1);
    const poll = http.get(`${BASE_URL}/api/v1/coupons/issue/${requestId}`, { headers });
    if (poll.status !== 200) {
      unexpected.add(true);
      continue;
    }
    const status = poll.json("data.status") || poll.json("status");
    if (status === "ISSUED") {
      issued.add(1);
      decisionMs.add(Date.now() - startedAt);
      unexpected.add(false);
      return;
    }
    if (status === "REJECTED") {
      rejectedAtConsumer.add(1);
      decisionMs.add(Date.now() - startedAt);
      unexpected.add(false);
      return;
    }
  }
  undecided.add(1); // 10초 내 미확정 — 확정 게이트 위반으로 별도 집계
  decisionMs.add(Date.now() - startedAt);
}
