import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";

const readLatency = new Trend("read_latency", true);
const writeLatency = new Trend("write_latency", true);
const tenantLeaks = new Counter("tenant_leak_indicators");

const baseUrl = __ENV.BASE_URL || "http://127.0.0.1:6060";
const tenantCode = __ENV.TENANT_CODE || "PERF0001";
const email = __ENV.EMAIL || "owner1@perf1.test";
const password = __ENV.PASSWORD || "admin123";

export const options = {
  scenarios: {
    reads: {
      executor: "ramping-vus",
      exec: "reads",
      startVUs: 5,
      stages: [
        { duration: __ENV.RAMP_DURATION || "1m", target: Number(__ENV.READ_VUS || 50) },
        { duration: __ENV.HOLD_DURATION || "5m", target: Number(__ENV.READ_VUS || 50) },
        { duration: "30s", target: 0 },
      ],
    },
    writes: {
      executor: "constant-arrival-rate",
      exec: "writes",
      rate: Number(__ENV.WRITE_RATE || 5),
      timeUnit: "1s",
      duration: __ENV.HOLD_DURATION || "5m",
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
  thresholds: {
    read_latency: ["p(95)<500"],
    write_latency: ["p(95)<800"],
    http_req_failed: ["rate<0.01"],
    tenant_leak_indicators: ["count==0"],
    dropped_iterations: ["count==0"],
  },
};

function authenticate() {
  const response = http.post(
    `${baseUrl}/api/app/auth/login`,
    JSON.stringify({ email, password, tenantCode }),
    { headers: { "Content-Type": "application/json" }, tags: { operation: "login" } },
  );
  check(response, { "login succeeds": (result) => result.status === 200 });
  return response.json("data.accessToken");
}

export function setup() {
  return { token: authenticate() };
}

function get(path, token, operation, expectedStatus = 200) {
  const expectedStatuses = Array.isArray(expectedStatus)
    ? expectedStatus
    : [expectedStatus];
  const response = http.get(`${baseUrl}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { operation },
    responseCallback: http.expectedStatuses(...expectedStatuses),
  });
  readLatency.add(response.timings.duration, { operation });
  check(response, {
    [`${operation} returns an expected status`]: (result) =>
      expectedStatuses.includes(result.status),
  });
  if (response.body && /@perf(?!1\\.)\\d+\\.test/i.test(response.body)) tenantLeaks.add(1);
}

export function reads(data) {
  get("/api/app/dashboard/summary", data.token, "dashboard");
  get("/api/app/customers?page=0&size=20&sort=createdAt&direction=DESC", data.token, "customers");
  get("/api/app/search?q=Customer&limit=10", data.token, "search", [200, 429]);
  get("/api/app/reports?timezone=Asia%2FKolkata", data.token, "reports");
  sleep(Math.random() * 2);
}

export function writes(data) {
  const suffix = `${__VU}-${__ITER}-${Date.now()}`;
  const response = http.post(
    `${baseUrl}/api/app/customers`,
    JSON.stringify({
      name: `Load Customer ${suffix}`,
      email: `load-${suffix}@perf.test`,
      notes: "Synthetic k6 write",
      active: true,
    }),
    {
      headers: {
        Authorization: `Bearer ${data.token}`,
        "Content-Type": "application/json",
      },
      tags: { operation: "create_customer" },
    },
  );
  writeLatency.add(response.timings.duration, { operation: "create_customer" });
  check(response, { "customer write succeeds": (result) => result.status === 201 || result.status === 200 });
}
