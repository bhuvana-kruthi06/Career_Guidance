#!/usr/bin/env python3
"""Check HTTPS links in the curated career skill resource catalogue."""
from __future__ import annotations

import json
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "data" / "resources" / "skill_resources.json"


def check_url(url: str, timeout: float = 12.0) -> tuple[bool, str]:
    if not url.startswith("https://"):
        return False, "URL must use HTTPS"
    for method in ("HEAD", "GET"):
        try:
            request = Request(url, method=method, headers={"User-Agent": "CareerGuidanceLinkCheck/1.0"})
            with urlopen(request, timeout=timeout) as response:
                if 200 <= response.status < 400:
                    return True, str(response.status)
                return False, str(response.status)
        except HTTPError as exc:
            if method == "HEAD" and exc.code in (403, 405, 501):
                continue
            return False, str(exc.code)
        except (URLError, TimeoutError, OSError) as exc:
            return False, type(exc).__name__
    return False, "not reachable"


def main() -> int:
    try:
        resources = json.loads(CATALOG.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"Could not read {CATALOG}: {exc}", file=sys.stderr)
        return 2
    if not isinstance(resources, list):
        print("Resource catalog must be a JSON array.", file=sys.stderr)
        return 2
    dead = []
    checked: dict[str, tuple[bool, str]] = {}
    for resource in resources:
        skill, url = resource.get("skill", "?"), resource.get("url", "")
        if url not in checked:
            checked[url] = check_url(url)
        ok, detail = checked[url]
        print(f"{'OK' if ok else 'DEAD'} {skill}: {detail}")
        if not ok:
            dead.append((skill, url, detail))
    print(f"Checked {len(checked)} unique links for {len(resources)} skills; {len(dead)} resource entries failed.")
    return 1 if dead else 0


if __name__ == "__main__":
    raise SystemExit(main())
