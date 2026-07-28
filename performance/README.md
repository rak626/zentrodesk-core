# Performance and resilience verification

Run only against an isolated, disposable environment. Authentication, rate limits,
tenant filters, PostgreSQL, and Redis must use production-equivalent settings.

## Dataset

Start the backend with the `dev` seed, then load `performance/seed.sql`:

```bash
psql "$DB_URL" -v ON_ERROR_STOP=1 -f performance/seed.sql
```

The idempotent seed adds 500 tenants, 10,000 fixed-role users, 20,000 customers,
30,000 leads, 10,000 bookings, and 10,000 payments. `PERF0001` is the default
load-test tenant. All identities and records are synthetic.

## Load profile

```bash
k6 run --summary-export artifacts/performance/summary.json performance/core-journeys.js
```

Tune `READ_VUS`, `WRITE_RATE`, `RAMP_DURATION`, and `HOLD_DURATION` without
changing the checked-in thresholds. Reads must remain below 500 ms p95, writes
below 800 ms p95, failures below 1%, and tenant-leak indicators at zero.

Capture Prometheus JVM, Hikari, HTTP, PostgreSQL, Redis, CPU, memory, and
connection metrics for the entire run. Record the saturation point, pool
headroom, slowest queries, frontend Lighthouse evidence, and exceptions in
`results-template.md`.

For a local contract smoke after loading the synthetic dataset into the
isolated E2E stack:

```bash
docker run --rm --user "$(id -u):$(id -g)" \
  --network zentrodesk-e2e_default \
  -e BASE_URL=http://backend:6060 \
  -e RAMP_DURATION=5s \
  -e HOLD_DURATION=10s \
  -e READ_VUS=2 \
  -e WRITE_RATE=1 \
  -v "$PWD/performance:/scripts:ro" \
  -v "$PWD/../zentrodesk-ui/artifacts/performance:/artifacts" \
  grafana/k6@sha256:e7eeddf1ce2361df6920d925297f487c0ba549c44be242c6a9c22f28d9b08efa \
  run --summary-export /artifacts/plan31-smoke.json /scripts/core-journeys.js
```

This smoke validates request contracts and thresholds only. It does not close
the production-equivalent saturation and dependency-failure evidence required
by Plan 33.

## Dependency exercises

During a steady low-volume profile, exercise one dependency at a time:

1. Stop Redis. Existing API bearer requests must continue; BFF requests must
   return bounded `503` responses and recover after Redis restarts.
2. Block PostgreSQL for 30 seconds. Requests must time out without thread or
   connection-pool exhaustion and recover without data corruption.
3. Disable email delivery. Transactional work must commit to the outbox and
   retry later without duplicate domain mutations.
4. Expire many access tokens together. The BFF single-flights refreshes per
   session; refresh and database pools must retain headroom.

Never run fault injection against production.
