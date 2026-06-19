import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const page = __ENV.PAGE || '0';
const size = __ENV.SIZE || '20';
const rate = Number(__ENV.K6_RATE || '200');
const preAllocatedVUs = Number(__ENV.K6_PRE_ALLOCATED_VUS || '100');
const maxVUs = Number(__ENV.K6_MAX_VUS || '500');

export const productListLikesDescLatency = new Trend('product_list_likes_desc_latency');
export const productListLikesDescErrors = new Rate('product_list_likes_desc_errors');

export const options = {
  scenarios: {
    product_list_likes_desc_peak: {
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
    product_list_likes_desc_errors: ['rate<0.01'],
  },
};

export default function () {
  const url = `${baseUrl}/api/v1/products?sort=likes_desc&page=${page}&size=${size}`;
  const response = http.get(url, {
    tags: {
      endpoint: 'products_likes_desc',
    },
  });

  productListLikesDescLatency.add(response.timings.duration);
  productListLikesDescErrors.add(response.status >= 400);

  check(response, {
    'status is 200': (res) => res.status === 200,
    'body contains data envelope': (res) => res.body.includes('"data"'),
  });
}
