"""Refresh the frontend's real O*NET skill catalog fallback from processed data."""
import json
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "data" / "processed" / "career_skill_map.csv"
OUTPUT = ROOT / "frontend" / "public" / "skills.json"


def main() -> None:
    frame = pd.read_csv(SOURCE, usecols=["element_id", "element_name", "skill_category"], dtype=str)
    skills = frame.drop_duplicates("element_id").sort_values("element_name")
    records = [
        {"id": row.element_id, "name": row.element_name, "category": row.skill_category}
        for row in skills.itertuples(index=False)
    ]
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(records)} O*NET skill entries to {OUTPUT}")


if __name__ == "__main__":
    main()
