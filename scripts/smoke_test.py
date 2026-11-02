"""Exercise the live career API with standard-library HTTP requests.

Start the Spring Boot API first, then run this script from the project root:
    python scripts/smoke_test.py
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


CORE_SKILLS = {
    "Complex Problem Solving": 5,
    "Critical Thinking": 5,
    "Active Listening": 5,
    "Writing": 5,
    "Programming": 5,
    "Time Management": 5,
}


def send_json(url: str, payload: dict | None = None) -> tuple[int, object]:
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST" if data is not None else "GET",
    )
    try:
        response = urlopen(request, timeout=15)
    except HTTPError as error:
        response = error
    except URLError as error:
        raise RuntimeError(f"Could not reach {url}: {error.reason}") from error
    with response:
        body = response.read().decode("utf-8")
        try:
            parsed = json.loads(body) if body else None
        except json.JSONDecodeError as error:
            raise RuntimeError(f"{url} returned invalid JSON (HTTP {response.status}).") from error
        return response.status, parsed


def main() -> int:
    parser = argparse.ArgumentParser(description="Smoke-test the live career guidance API.")
    parser.add_argument(
        "--base-url",
        default=os.environ.get("API_BASE_URL", "http://127.0.0.1:8091"),
        help="API origin (default: API_BASE_URL or http://127.0.0.1:8091)",
    )
    args = parser.parse_args()
    base_url = args.base_url.rstrip("/")

    try:
        status, skills = send_json(base_url + "/api/skills")
        if status != 200 or not isinstance(skills, list) or not skills:
            raise RuntimeError(f"GET /api/skills expected a non-empty JSON list; got HTTP {status}.")
        print(f"PASS GET /api/skills ({len(skills)} skills)")

        valid_request = {
            "studentSkills": CORE_SKILLS,
            "studentInterests": ["I"],
            "topN": 3,
            "assessedOnly": True,
        }
        status, matches = send_json(base_url + "/api/matches", valid_request)
        if status != 200 or not isinstance(matches, list) or not matches:
            raise RuntimeError(f"Valid POST /api/matches expected career results; got HTTP {status}.")
        if not isinstance(matches[0], dict) or "occupation_title" not in matches[0]:
            raise RuntimeError("POST /api/matches returned an unexpected result shape.")
        print(f"PASS POST /api/matches ({len(matches)} results)")

        invalid_request = {
            "studentSkills": {"Critical Thinking": 9},
            "studentInterests": ["I"],
            "topN": 3,
            "assessedOnly": False,
        }
        status, _ = send_json(base_url + "/api/matches", invalid_request)
        if status != 400:
            raise RuntimeError(f"Invalid POST /api/matches expected HTTP 400; got HTTP {status}.")
        print("PASS POST /api/matches rejects invalid ratings (HTTP 400)")
        print("Smoke test passed.")
        return 0
    except (RuntimeError, OSError) as error:
        print(f"Smoke test failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
