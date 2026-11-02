"""Measure local matching API latency using only the Python standard library."""
from __future__ import annotations

import argparse
import concurrent.futures
import json
import statistics
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

PAYLOAD = {
    "studentSkills": {
        "Complex Problem Solving": 3,
        "Critical Thinking": 4,
        "Active Listening": 3,
        "Writing": 3,
        "Programming": 4,
        "Time Management": 3,
    },
    "studentInterests": ["I"],
    "topN": 10,
    "assessedOnly": True,
}


def percentile(values: list[float], p: float) -> float:
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, max(0, int((len(ordered) * p + 0.999999) - 1)))]


def sample(base_url: str, _: int) -> float:
    body = json.dumps(PAYLOAD).encode("utf-8")
    request = Request(base_url.rstrip("/") + "/api/matches", data=body,
                      headers={"Content-Type": "application/json"}, method="POST")
    started = time.perf_counter()
    try:
        with urlopen(request, timeout=120) as response:
            response.read()
            if response.status != 200:
                raise RuntimeError(f"match returned HTTP {response.status}")
    except (HTTPError, URLError) as exc:
        raise RuntimeError(f"benchmark request failed: {exc}") from exc
    return (time.perf_counter() - started) * 1000


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8091")
    parser.add_argument("--iterations", type=int, default=20)
    args = parser.parse_args()
    if args.iterations < 2:
        parser.error("--iterations must be at least 2 for p95")
    print(f"API={args.base_url} iterations={args.iterations}")
    for concurrency in (1, 10):
        started = time.perf_counter()
        with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as pool:
            latencies = list(pool.map(lambda i: sample(args.base_url, i), range(args.iterations)))
        print(json.dumps({
            "concurrency": concurrency,
            "requests": len(latencies),
            "p50_ms": round(statistics.median(latencies), 1),
            "p95_ms": round(percentile(latencies, 0.95), 1),
            "elapsed_ms": round((time.perf_counter() - started) * 1000, 1),
        }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
