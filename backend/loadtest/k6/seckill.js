import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8081';
const voucherId = __ENV.VOUCHER_ID;
const tokenFile = __ENV.AUTH_TOKENS_FILE;
const scenarioMode = getEnv('LOAD_SCENARIO_MODE', 'K6_SCENARIO_MODE', 'ramping-vus').toLowerCase();
const sleepMs = parseIntegerEnv('LOAD_SLEEP_MS', parseIntegerEnv('K6_SLEEP_MS', 0));

let tokenSource = __ENV.AUTH_TOKENS || __ENV.AUTH_TOKEN || '';
if (!tokenSource && tokenFile) {
  tokenSource = open(tokenFile);
}

const authTokens = tokenSource
  .split(/[\n,]/)
  .map((item) => item.trim())
  .filter((item) => item.length > 0);

if (!voucherId) {
  throw new Error('Set VOUCHER_ID before running the k6 seckill script.');
}

if (authTokens.length === 0) {
  throw new Error('Provide AUTH_TOKENS, AUTH_TOKEN, or AUTH_TOKENS_FILE before running k6.');
}

export const options = {
  scenarios: {
    seckill_pressure: buildScenario(),
  },
  thresholds: {
    http_req_failed: ['rate<0.30'],
    http_req_duration: ['p(95)<3000'],
  },
};

const seckillRequests = new Counter('seckill_requests');
const businessLatency = new Trend('seckill_business_duration', true);

function parseIntegerEnv(name, fallbackValue) {
  const raw = __ENV[name];
  if (!raw) return fallbackValue;
  const value = Number.parseInt(raw, 10);
  return Number.isFinite(value) ? value : fallbackValue;
}

function getEnv(primaryName, legacyName, fallbackValue) {
  return __ENV[primaryName] || __ENV[legacyName] || fallbackValue;
}

function parseStages(raw, fallbackValue) {
  const stageSpec = raw || fallbackValue;
  return stageSpec
    .split(',')
    .map((segment) => segment.trim())
    .filter((segment) => segment.length > 0)
    .map((segment) => {
      const [duration, target] = segment.split(':').map((item) => item.trim());
      if (!duration || !target) {
        throw new Error(`Invalid K6 stage segment: ${segment}`);
      }
      return {
        duration,
        target: Number.parseInt(target, 10),
      };
    });
}

function buildScenario() {
  if (scenarioMode === 'ramping-arrival-rate' || scenarioMode === 'arrival-rate') {
    return {
      executor: 'ramping-arrival-rate',
      startRate: parseIntegerEnv('LOAD_START_RATE', parseIntegerEnv('K6_START_RATE', 200)),
      timeUnit: getEnv('LOAD_TIME_UNIT', 'K6_TIME_UNIT', '1s'),
      preAllocatedVUs: parseIntegerEnv('LOAD_PRE_ALLOCATED_VUS', parseIntegerEnv('K6_PRE_ALLOCATED_VUS', 300)),
      maxVUs: parseIntegerEnv('LOAD_MAX_VUS', parseIntegerEnv('K6_MAX_VUS', 4000)),
      stages: parseStages(
        getEnv('LOAD_STAGES', 'K6_STAGES', null),
        '15s:300,15s:800,30s:1500,30s:3000,20s:0'
      ),
      gracefulStop: getEnv('LOAD_GRACEFUL_STOP', 'K6_GRACEFUL_STOP', '10s'),
    };
  }

  return {
    executor: 'ramping-vus',
    stages: parseStages(
      getEnv('LOAD_STAGES', 'K6_STAGES', null),
      '15s:200,15s:500,30s:1000,30s:2000,20s:0'
    ),
    gracefulRampDown: getEnv('LOAD_GRACEFUL_RAMP_DOWN', 'K6_GRACEFUL_RAMP_DOWN', '10s'),
  };
}

function pickToken() {
  const index = (__ITER + __VU - 1) % authTokens.length;
  return authTokens[index];
}

function classifyResult(body, response) {
  if (response.status !== 200) return `http_${response.status}`;
  if (!body || typeof body !== 'object') return 'unknown';
  if (body.success === true) return 'success';

  const message = body.errorMsg || '';
  if (message.includes('\u5e93\u5b58\u4e0d\u8db3')) return 'stock_insufficient';
  if (message.includes('\u91cd\u590d\u4e0b\u5355')) return 'duplicate_order';
  if (message.includes('\u7a0d\u540e\u91cd\u8bd5') || message.includes('\u7f51\u7edc\u6b63\u5fd9')) {
    return 'rate_limited';
  }
  if (message.includes('\u672a\u767b\u5f55') || message.toLowerCase().includes('token')) return 'unauthorized';
  return 'business_failed';
}

export default function () {
  const token = pickToken();
  const url = `${baseUrl}/voucher-order/seckill/${voucherId}`;

  const response = http.post(url, null, {
    headers: {
      Authorization: token,
    },
    tags: {
      api: 'seckill',
      voucher_id: voucherId,
      scenario_mode: scenarioMode,
    },
  });

  let body = null;
  try {
    body = response.json();
  } catch (e) {
    body = null;
  }

  const result = classifyResult(body, response);
  seckillRequests.add(1, { result, scenario_mode: scenarioMode });
  businessLatency.add(response.timings.duration, { result, scenario_mode: scenarioMode });

  check(response, {
    'http status is 200': (res) => res.status === 200,
  });

  if (sleepMs > 0) {
    sleep(sleepMs / 1000);
  }
}
