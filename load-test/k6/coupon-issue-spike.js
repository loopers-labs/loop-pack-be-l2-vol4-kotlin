// S1 스파이크 — 10초 내 1만 요청을 한도 100 쿠폰에 집중.
// 검증 목표: 발급 정확히 100 · 나머지 SOLD_OUT · 5xx/네트워크 오류 0 · 결정 지연 분포.
// 실행:
//   BASE_URL=http://127.0.0.1:8080 COUPON_ID=90001 k6 run load-test/k6/coupon-issue-spike.js
// 시드: load-test/coupon-perf-seed.sql (cpuser00001~cpuser10000, 쿠폰 90001=한도100)
import http from "k6/http";
import exec from "k6/execution";
import { Counter, Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";
const COUPON_ID = Number(__ENV.COUPON_ID || 90001);
const PASSWORD = __ENV.PASSWORD || "Perf@Loop2026";

// 200=발급, 409=거절(SOLD_OUT/ALREADY_ISSUED) 은 정상 결정. 그 외만 실패로 집계.
http.setResponseCallback(http.expectedStatuses(200, 409));

const issued = new Counter("coupon_issued");
const rejected = new Counter("coupon_rejected");
const decisionMs = new Trend("coupon_decision_ms", true);
const unexpected = new Rate("coupon_unexpected");

export const options = {
  scenarios: {
    spike: {
      executor: "constant-arrival-rate",
      rate: 1000, // 1000/s × 10s = 10,000 요청
      timeUnit: "1s",
      duration: "10s",
      preAllocatedVUs: 500,
      maxVUs: 2000,
    },
  },
  thresholds: {
    coupon_unexpected: ["rate<0.01"], // 5xx/네트워크 오류 1% 미만
  },
};

function loginId() {
  // iterationInTest: 0..9999 → cpuser00001..cpuser10000 (요청마다 distinct 유저 = userId 중복 0 검증)
  const n = exec.scenario.iterationInTest + 1;
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
