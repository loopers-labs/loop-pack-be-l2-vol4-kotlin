import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const password = __ENV.USER_PASSWORD || 'abcd1234';
const userPrefix = __ENV.USER_PREFIX || 'cartuser';
const userStart = Number(__ENV.USER_START || '1');
const userCount = Number(__ENV.USER_COUNT || '1000');
const expectedCount = Number(__ENV.EXPECTED_COUNT || '10');
const rate = Number(__ENV.K6_RATE || '200');
const preAllocatedVUs = Number(__ENV.K6_PRE_ALLOCATED_VUS || '100');
const maxVUs = Number(__ENV.K6_MAX_VUS || '500');

export const cartCountLatency = new Trend('cart_count_latency');
export const cartCountErrors = new Rate('cart_count_errors');

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    cart_count_peak: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs,
      maxVUs,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    cart_count_errors: ['rate<0.01'],
  },
};

export default function () {
  const userNumber = userStart + (exec.scenario.iterationInTest % userCount);
  const loginId = `${userPrefix}${String(userNumber).padStart(4, '0')}`;
  const response = http.get(`${baseUrl}/api/v1/cart/count`, {
    headers: {
      'X-Loopers-LoginId': loginId,
      'X-Loopers-LoginPw': password,
    },
    tags: {
      endpoint: 'cart_count',
    },
  });

  let countOk = false;
  try {
    countOk = response.json()?.data?.count === expectedCount;
  } catch (_) {
    countOk = false;
  }

  cartCountLatency.add(response.timings.duration);
  cartCountErrors.add(response.status !== 200 || !countOk);

  check(response, {
    'status is 200': (res) => res.status === 200,
    'count is expected': () => countOk,
  });
}
