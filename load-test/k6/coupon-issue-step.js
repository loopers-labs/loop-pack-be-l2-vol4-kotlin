// S2 계단 — 100→200→400→800 req/s 로 도착률을 올리며 한도 10만 쿠폰에 지속 경합.
// 매진 없이 한 hot row 락 경합 하에서 throughput/latency 가 어디서 꺾이는지(포화점) 관찰.
// 실행:
//   BASE_URL=http://127.0.0.1:8080 COUPON_ID=90002 k6 run load-test/k6/coupon-issue-step.js
//
// 유저 재활용: 시드 유저 1만 명을 순환. 첫 발급 뒤 재요청은 ALREADY_ISSUED(409)로 떨어지지만,
// 그 경로도 쿠폰 row 락 획득→issue()→중복 감지→롤백까지 밟으므로 락 경합 측정에는 동일하게 유효.
import http from "k6/http";
import exec from "k6/execution";
import { Counter, Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";
const COUPON_ID = Number(__ENV.COUPON_ID || 90002);
const PASSWORD = __ENV.PASSWORD || "Perf@Loop2026";
const USER_POOL = Number(__ENV.USER_POOL || 10000);
const STAGE_DUR = __ENV.STAGE_DUR || "3m";

http.setResponseCallback(http.expectedStatuses(200, 409));

const issued = new Counter("coupon_issued");
const rejected = new Counter("coupon_rejected");
const decisionMs = new Trend("coupon_decision_ms", true);
const unexpected = new Rate("coupon_unexpected");

export const options = {
  scenarios: {
    step: {
      executor: "ramping-arrival-rate",
      startRate: 100,
      timeUnit: "1s",
      preAllocatedVUs: 200,
      maxVUs: 2000,
      stages: [
        { target: 100, duration: STAGE_DUR },
        { target: 200, duration: STAGE_DUR },
        { target: 400, duration: STAGE_DUR },
        { target: 800, duration: STAGE_DUR },
      ],
    },
  },
  thresholds: {
    coupon_unexpected: ["rate<0.01"],
  },
};

function loginId() {
  const n = (exec.scenario.iterationInTest % USER_POOL) + 1;
  return "cpuser" + String(n).padStart(5, "0");
}

export default function () {
  const res = http.post(
    `${BASE_URL}/api/v1/coupons/issue`,
    JSON.stringify({ couponId: COUPON_ID }),
    {
      headers: {
        "Content-Type": "application/json",
        "X-Loopers-LoginId": loginId(),
        "X-Loopers-LoginPw": PASSWORD,
      },
    },
  );

  decisionMs.add(res.timings.duration);
  if (res.status === 200) {
    issued.add(1);
    unexpected.add(false);
  } else if (res.status === 409) {
    rejected.add(1);
    unexpected.add(false);
  } else {
    unexpected.add(true);
  }
}
