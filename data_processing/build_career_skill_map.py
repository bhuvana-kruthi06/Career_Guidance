"""
build_career_skill_map.py
-------------------------
Step 5 of the AI Career Guidance data pipeline.

PURPOSE:
    Reads the cleaned O*NET files and builds a unified career→skill lookup
    table: one row per (occupation, skill_element, scale).

    This table is the input to the matching engine.

DESIGN DECISIONS (defaults — can be changed in config below):
    - Skill dimensions used: Essential (10) + Transferable (25) + Knowledge (33)
      = 68 dimensions per occupation. Abilities (52) excluded by default to keep
      the model focused on learnable skills rather than innate abilities.
    - Dataset: O*NET 31.0 only (richer importance+level scoring than ESCO).
    - ESCO skills added as a supplementary lookup (not used for scoring yet).
    - Only Importance (IM) scale rows used for weighting; Level (LV) rows used
      for proficiency threshold.

OUTPUT:
    data/processed/career_skill_map.csv
        Columns: onet_soc_code, occupation_title, element_id, element_name,
                 skill_category, importance_score, level_score, job_zone,
                 riasec_top1, riasec_top2, riasec_top3

    data/processed/career_metadata.csv
        Columns: onet_soc_code, occupation_title, description, job_zone,
                 riasec_top1, riasec_top2, riasec_top3

USAGE:
    python data_processing/build_career_skill_map.py
"""

import sys
import pandas as pd
from pathlib import Path
from datetime import datetime

# ── Paths ────────────────────────────────────────────────────────────────────
PROJECT_ROOT = Path(__file__).resolve().parent.parent
PROCESSED    = PROJECT_ROOT / "data" / "processed"

# ── Configuration (edit here to change defaults) ──────────────────────────────
# Skill files to include in career matching. Each maps to a category label.
# Abilities.xlsx is excluded — innate abilities are harder to self-assess.
# Add ("onet_abilities_clean.csv", "Ability") to include abilities.
SKILL_FILES = [
    ("onet_essential_skills_clean.csv",    "Essential"),
    ("onet_transferable_skills_clean.csv", "Transferable"),
    ("onet_knowledge_clean.csv",           "Knowledge"),
]

# Minimum importance score to include a skill in the map.
# Skills with importance < this threshold are excluded (low-relevance noise).
# O*NET importance scale: 1.0 (not important) to 5.0 (extremely important)
MIN_IMPORTANCE = 1.5   # Illustrative default — adjust after validation

# ── Helpers ───────────────────────────────────────────────────────────────────

def load_csv(filename: str) -> pd.DataFrame | None:
    path = PROCESSED / filename
    if not path.exists():
        print(f"  [MISSING] {filename} — run clean_data.py first")
        return None
    return pd.read_csv(path, encoding="utf-8", low_memory=False)


def build_skill_pivot(df: pd.DataFrame, category: str) -> pd.DataFrame:
    """
    From a long-format O*NET skill file (one row per occ+skill+scale),
    pivot to wide format: one row per (occ, skill) with both IM and LV scores.
    """
    im = df[df["Scale ID"] == "IM"][
        ["O*NET-SOC Code", "Title", "Element ID", "Element Name", "Data Value"]
    ].rename(columns={"Data Value": "importance_score"})

    lv = df[df["Scale ID"] == "LV"][
        ["O*NET-SOC Code", "Element ID", "Data Value"]
    ].rename(columns={"Data Value": "level_score"})

    merged = im.merge(lv, on=["O*NET-SOC Code", "Element ID"], how="left")
    merged["skill_category"] = category
    merged = merged.rename(columns={
        "O*NET-SOC Code": "onet_soc_code",
        "Title":          "occupation_title",
        "Element ID":     "element_id",
        "Element Name":   "element_name",
    })
    return merged[[
        "onet_soc_code", "occupation_title", "element_id",
        "element_name", "skill_category", "importance_score", "level_score"
    ]]


def build_riasec_lookup() -> pd.DataFrame:
    """
    Build a lookup: onet_soc_code → (riasec_top1, riasec_top2, riasec_top3)
    from the Career Interest Types file.
    """
    ci = load_csv("onet_career_interests_clean.csv")
    if ci is None:
        return pd.DataFrame()

    # Filter to the ranked interest columns only
    ranked = ci[ci["Element Name"].isin([
        "First Interest High-Point",
        "Second Interest High-Point",
        "Third Interest High-Point",
    ])].copy()

    # The Data Value for ranked interests is a numeric code mapping to RIASEC type
    # Map: Realistic=1, Investigative=2, Artistic=3, Social=4, Enterprising=5, Conventional=6
    riasec_map = {1.0: "R", 2.0: "I", 3.0: "A", 4.0: "S", 5.0: "E", 6.0: "C"}
    ranked["riasec_code"] = ranked["Data Value"].map(riasec_map).fillna("?")

    pivot = ranked.pivot_table(
        index="O*NET-SOC Code",
        columns="Element Name",
        values="riasec_code",
        aggfunc="first"
    ).reset_index()

    pivot = pivot.rename(columns={
        "O*NET-SOC Code":             "onet_soc_code",
        "First Interest High-Point":  "riasec_top1",
        "Second Interest High-Point": "riasec_top2",
        "Third Interest High-Point":  "riasec_top3",
    })
    return pivot


def build_job_zone_lookup() -> pd.DataFrame:
    """Build onet_soc_code → job_zone lookup."""
    jz = load_csv("onet_job_zones_clean.csv")
    if jz is None:
        return pd.DataFrame()
    return jz[["O*NET-SOC Code", "Job Zone"]].rename(columns={
        "O*NET-SOC Code": "onet_soc_code",
        "Job Zone":       "job_zone",
    })


# ── Main ─────────────────────────────────────────────────────────────────────

def run_build_map() -> None:
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print("=" * 70)
    print("  CAREER-SKILL MAP BUILDER")
    print(f"  Started: {timestamp}")
    print(f"  Skill dimensions: {[s[1] for s in SKILL_FILES]}")
    print(f"  Min importance threshold: {MIN_IMPORTANCE}")
    print("=" * 70)

    # ── 1. Load and stack all skill files ─────────────────────────────────────
    all_skills = []
    for fname, category in SKILL_FILES:
        print(f"\nLoading {fname} ({category}) ...")
        df = load_csv(fname)
        if df is None:
            continue
        pivoted = build_skill_pivot(df, category)
        before = len(pivoted)
        pivoted = pivoted[pivoted["importance_score"] >= MIN_IMPORTANCE]
        after = len(pivoted)
        print(f"  Rows: {before:,} → {after:,} after importance filter (≥{MIN_IMPORTANCE})")
        all_skills.append(pivoted)

    if not all_skills:
        print("ERROR: No skill files loaded. Run clean_data.py first.")
        sys.exit(1)

    skill_map = pd.concat(all_skills, ignore_index=True)
    print(f"\nCombined skill map: {len(skill_map):,} rows")
    print(f"  Unique occupations: {skill_map['onet_soc_code'].nunique():,}")
    print(f"  Unique skill elements: {skill_map['element_id'].nunique():,}")

    # ── 2. Attach Job Zone ────────────────────────────────────────────────────
    print("\nAttaching Job Zones ...")
    jz = build_job_zone_lookup()
    if not jz.empty:
        skill_map = skill_map.merge(jz, on="onet_soc_code", how="left")
        print(f"  Job Zone coverage: {skill_map['job_zone'].notna().sum():,} rows matched")
    else:
        skill_map["job_zone"] = None

    # ── 3. Attach RIASEC ──────────────────────────────────────────────────────
    print("\nAttaching RIASEC interest codes ...")
    riasec = build_riasec_lookup()
    if not riasec.empty:
        skill_map = skill_map.merge(riasec, on="onet_soc_code", how="left")
        print(f"  RIASEC coverage: {skill_map['riasec_top1'].notna().sum():,} rows matched")
    else:
        skill_map["riasec_top1"] = None
        skill_map["riasec_top2"] = None
        skill_map["riasec_top3"] = None

    # ── 4. Save career-skill map ──────────────────────────────────────────────
    out_map = PROCESSED / "career_skill_map.csv"
    skill_map.to_csv(out_map, index=False, encoding="utf-8")
    print(f"\n  Saved: career_skill_map.csv ({len(skill_map):,} rows)")

    # ── 5. Build and save career metadata ─────────────────────────────────────
    print("\nBuilding career metadata ...")
    occ = load_csv("onet_occupation_data_clean.csv")
    if occ is not None:
        meta = occ.rename(columns={
            "O*NET-SOC Code": "onet_soc_code",
            "Title":          "occupation_title",
            "Description":    "description",
        })[["onet_soc_code", "occupation_title", "description"]]

        # Attach job zone and RIASEC to metadata too
        if not jz.empty:
            meta = meta.merge(jz, on="onet_soc_code", how="left")
        if not riasec.empty:
            meta = meta.merge(riasec, on="onet_soc_code", how="left")

        out_meta = PROCESSED / "career_metadata.csv"
        meta.to_csv(out_meta, index=False, encoding="utf-8")
        print(f"  Saved: career_metadata.csv ({len(meta):,} careers)")

    # ── Summary ───────────────────────────────────────────────────────────────
    print("\n" + "=" * 70)
    print("  CAREER-SKILL MAP BUILT SUCCESSFULLY")
    print(f"  career_skill_map.csv  : {len(skill_map):,} rows")
    cats = skill_map["skill_category"].value_counts()
    for cat, cnt in cats.items():
        print(f"      {cat:<20} {cnt:,} rows")
    print("=" * 70)


if __name__ == "__main__":
    if not PROCESSED.exists():
        print("ERROR: data/processed/ not found. Run clean_data.py first.")
        sys.exit(1)
    run_build_map()
