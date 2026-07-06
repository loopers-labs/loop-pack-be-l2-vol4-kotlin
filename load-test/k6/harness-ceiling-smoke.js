// 하네스 천장 스모크 — 경로(SSH 터널 / localhost k6)가 병목이 아님을 확인하는 사전 검사.
// 서버 비용이 ~0 인 무의존 헬스 경로(actuator liveness)에 도착률을 올려, 하네스가 낼 수 있는
// 최대 RPS 와 그때 p99 를 잰다. 이 천장이 SUT 포화점의 3배 이상이면 하네스는 병목이 아니다.
// (인증 BCrypt 같은 '상수 세금'과 달리, 하네스 '천장'은 측정 대상을 서버→하네스로 바꾼다.
//  WRITING-LOG 결정 9-보강.)
//
// 실행 (management.server.port=8081, /actuator 는 무인증):
//   SMOKE_URL=http://127.0.0.1:8081/actuator/health/liveness k6 run load-test/k6/harness-ceiling-smoke.js
import http from "k6/http";
import { Trend } from "k6/metrics";

const SMOKE_URL = __ENV.SMOKE_URL || "http://127.0.0.1:8081/actuator/health/liveness";

const rtt = new Trend("smoke_rtt_ms", true);

export const options = {
  scenarios: {
    ceiling: {
      executor: "ramping-arrival-rate",
      startRate: 500,
      timeUnit: "1s",
      preAllocatedVUs: 500,
      maxVUs: 4000,
      stages: [
        { target: 500, duration: "20s" },
        { target: 1000, duration: "20s" },
        { target: 2000, duration: "20s" },
        { target: 4000, duration: "20s" },
      ],
    },
  },
  // 임계 초과 = 그 도착률에서 하네스가 이미 꺾인 지점. 그 아래를 유효 천장으로 본다.
  thresholds: {
    smoke_rtt_ms: ["p(99)<50"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const res = http.get(SMOKE_URL);
  rtt.add(res.timings.duration);
}
