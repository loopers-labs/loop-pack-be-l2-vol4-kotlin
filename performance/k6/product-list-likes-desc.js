import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const brandId = __ENV.BRAND_ID || '1001';
const page = __ENV.PAGE || '0';
const size = __ENV.SIZE || '20';
const duration = __ENV.K6_DURATION || '2m';
const rate = Number(__ENV.K6_RATE || '200');
const preAllocatedVUs = Number(__ENV.K6_PRE_ALLOCATED_VUS || '100');
const maxVUs = Number(__ENV.K6_MAX_VUS || '500');
const perScenarioRate = Math.max(1, Math.floor(rate / 6));

export const productListLatestLatency = new Trend('product_list_latest_latency');
export const productListPriceAscLatency = new Trend('product_list_price_asc_latency');
export const productListLikesDescLatency = new Trend('product_list_likes_desc_latency');
export const brandProductListLatestLatency = new Trend('brand_product_list_latest_latency');
export const brandProductListPriceAscLatency = new Trend('brand_product_list_price_asc_latency');
export const brandProductListLikesDescLatency = new Trend('brand_product_list_likes_desc_latency');
export const productListErrors = new Rate('product_list_errors');

export const options = {
  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    product_list_latest: scenario('productListLatest'),
    product_list_price_asc: scenario('productListPriceAsc'),
    product_list_likes_desc: scenario('productListLikesDesc'),
    brand_product_list_latest: scenario('brandProductListLatest'),
    brand_product_list_price_asc: scenario('brandProductListPriceAsc'),
    brand_product_list_likes_desc: scenario('brandProductListLikesDesc'),
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    product_list_errors: ['rate<0.01'],
  },
};

function scenario(exec) {
  return {
    executor: 'constant-arrival-rate',
    exec,
    rate: perScenarioRate,
    timeUnit: '1s',
    duration,
    preAllocatedVUs: Math.max(1, Math.floor(preAllocatedVUs / 6)),
    maxVUs: Math.max(1, Math.floor(maxVUs / 6)),
  };
}

export function productListLatest() {
  requestProducts('/api/v1/products?sort=latest', productListLatestLatency, 'product_list_latest');
}

export function productListPriceAsc() {
  requestProducts('/api/v1/products?sort=price_asc', productListPriceAscLatency, 'product_list_price_asc');
}

export function productListLikesDesc() {
  requestProducts('/api/v1/products?sort=likes_desc', productListLikesDescLatency, 'product_list_likes_desc');
}

export function brandProductListLatest() {
  requestProducts(`/api/v1/brands/${brandId}/products?sort=latest`, brandProductListLatestLatency, 'brand_product_list_latest');
}

export function brandProductListPriceAsc() {
  requestProducts(
    `/api/v1/brands/${brandId}/products?sort=price_asc`,
    brandProductListPriceAscLatency,
    'brand_product_list_price_asc',
  );
}

export function brandProductListLikesDesc() {
  requestProducts(
    `/api/v1/brands/${brandId}/products?sort=likes_desc`,
    brandProductListLikesDescLatency,
    'brand_product_list_likes_desc',
  );
}

export default function () {
  productListLikesDesc();
}

function requestProducts(path, latencyMetric, endpoint) {
  const separator = path.includes('?') ? '&' : '?';
  const url = `${baseUrl}${path}${separator}page=${page}&size=${size}`;
  const response = http.get(url, { tags: { endpoint } });

  latencyMetric.add(response.timings.duration);
  productListErrors.add(response.status >= 400);

  check(response, {
    'status is 200': (res) => res.status === 200,
    'body contains data envelope': (res) => Boolean(res.body && res.body.includes('"data"')),
  });
}
