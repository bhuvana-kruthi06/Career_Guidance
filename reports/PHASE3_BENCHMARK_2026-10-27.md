# Phase 3 — Matcher benchmark record

## Baseline measurement status

The benchmark script is ready, but a numeric pre-hardening p50/p95 baseline could not be collected in this environment.

## Attempts and evidence

- `python scripts/benchmark_api.py --base-url http://127.0.0.1:8091 --iterations 20` was not run against port 8091 because an unknown listener already owned that port; it was left untouched.
- `Get-NetTCPConnection -LocalPort 8091 -State Listen` did not return a process owner from the available shell.
- Starting the current backend with Maven on its default port failed because port 8091 was already in use.
- Starting a temporary backend on port 8092 failed during local socket initialization with a Windows socket-access error.
- The already-running listener was not benchmarked or stopped. Its identity and version are unverified.

## Reproduction when a known local API is available

```powershell
python scripts/benchmark_api.py --base-url http://127.0.0.1:8091 --iterations 20
```

The script measures 20 successful match requests sequentially and 20 requests with 10 concurrent workers. It prints p50 and p95 request latency plus total elapsed time for each concurrency level.

## Worker decision

The persistent Python worker was **not implemented**. The plan requires before-and-after measurements to justify this architecture change, and no trustworthy baseline could be captured. The existing process-per-request path remains the safe default.
