const fs = require('fs');

const latencyMetrics = [
  'product_list_latest_latency',
  'product_list_price_asc_latency',
  'product_list_likes_desc_latency',
  'brand_product_list_latest_latency',
  'brand_product_list_price_asc_latency',
  'brand_product_list_likes_desc_latency',
];

const latencyStats = ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'];

const [beforePath, afterPath] = process.argv.slice(2);

if (!beforePath || !afterPath) {
  console.error('Usage: node performance/compare-k6-latency.js <before-summary.json> <after-summary.json>');
  process.exit(1);
}

const before = readSummary(beforePath);
const after = readSummary(afterPath);
const comparisons = latencyMetrics.map((metric) => compareMetric(metric, before.metrics[metric], after.metrics[metric]));
const allLatencyReduced = comparisons.every((comparison) => comparison.stats.every((stat) => stat.delta < 0));

console.log(JSON.stringify({
  allLatencyReduced,
  guardrails: {
    before: guardrails(before.metrics),
    after: guardrails(after.metrics),
  },
  comparisons,
}, null, 2));

process.exitCode = allLatencyReduced ? 0 : 2;

function readSummary(path) {
  return JSON.parse(fs.readFileSync(path, 'utf8'));
}

function compareMetric(name, beforeMetric, afterMetric) {
  if (!beforeMetric || !afterMetric) {
    return {
      name,
      stats: latencyStats.map((stat) => ({ stat, before: null, after: null, delta: null, deltaPercent: null })),
    };
  }

  return {
    name,
    stats: latencyStats.map((stat) => {
      const beforeValue = beforeMetric[stat];
      const afterValue = afterMetric[stat];
      const delta = afterValue - beforeValue;
      return {
        stat,
        before: beforeValue,
        after: afterValue,
        delta,
        deltaPercent: beforeValue === 0 ? null : (delta / beforeValue) * 100,
      };
    }),
  };
}

function guardrails(metrics) {
  return {
    requests: metrics.http_reqs?.count ?? null,
    requestRate: metrics.http_reqs?.rate ?? null,
    failedRate: metrics.http_req_failed?.value ?? null,
    checksRate: metrics.checks?.value ?? null,
    iterations: metrics.iterations?.count ?? null,
    droppedIterations: metrics.dropped_iterations?.count ?? 0,
    dataReceived: metrics.data_received?.count ?? null,
    dataSent: metrics.data_sent?.count ?? null,
  };
}
