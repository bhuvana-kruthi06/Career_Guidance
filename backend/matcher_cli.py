"""JSON bridge to the project's authoritative Python CareerMatcher."""
from __future__ import annotations

import json
import sys
from contextlib import redirect_stdout
from dataclasses import asdict
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))


def main() -> int:
    try:
        request = json.load(sys.stdin)
        if request.get("operation") == "skills":
            frame = pd.read_csv(ROOT / "data" / "processed" / "career_skill_map.csv", usecols=["element_id", "element_name", "skill_category"])
            skills = frame.drop_duplicates("element_id").sort_values("element_name")
            json.dump([{"id": row.element_id, "name": row.element_name, "category": row.skill_category} for row in skills.itertuples(index=False)], sys.stdout)
            return 0

        from data_processing.matching_engine import CareerMatcher

        with redirect_stdout(sys.stderr):
            matcher = CareerMatcher()
        results = matcher.match(
            student_skills=request["student_skills"],
            student_interests=request.get("student_interests") or None,
            top_n=request.get("top_n", 10),
            assessed_only=request.get("assessed_only", False),
        )
        json.dump([asdict(result) for result in results], sys.stdout, allow_nan=False)
        return 0
    except Exception as exc:  # surfaced by the API as a dependency failure
        print(f"Matcher error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
