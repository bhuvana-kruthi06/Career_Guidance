"""
normalize_skills.py
-------------------
Step 3 of the AI Career Guidance data pipeline.

PURPOSE:
    Normalises skill names across O*NET and ESCO datasets using a documented,
    editable alias map. Adds a 'skill_name_normalized' column to each output
    while preserving the original name and all source identifiers.

KEY RULES:
    - Original skill names are always kept in 'skill_name_original'.
    - Java and JavaScript are NEVER merged — they are treated as distinct skills.
    - Uncertain mappings are flagged with normalization_flag = REVIEW_NEEDED.
    - The ALIAS_MAP below is the single source of truth — edit it freely.
    - Normalisation is case-insensitive matching but output is always the
      canonical name defined in ALIAS_MAP.
    - Source dataset skill IDs (O*NET Element ID, ESCO concept URI) are
      preserved unchanged.

INPUTS:
    data/processed/onet_skills_clean.csv
    data/processed/onet_essential_skills_clean.csv
    data/processed/onet_software_skills_clean.csv
    data/processed/esco_skills_clean.csv

OUTPUTS:
    data/processed/onet_skills_normalized.csv
    data/processed/onet_essential_skills_normalized.csv
    data/processed/onet_software_skills_normalized.csv
    data/processed/esco_skills_normalized.csv
    data/processed/normalization_flagged_review.csv  ← uncertain mappings

USAGE:
    python data_processing/normalize_skills.py
"""

import sys
import pandas as pd
from pathlib import Path
from datetime import datetime

# ── Paths ────────────────────────────────────────────────────────────────────
PROJECT_ROOT = Path(__file__).resolve().parent.parent
PROCESSED    = PROJECT_ROOT / "data" / "processed"

# ── Alias Map ────────────────────────────────────────────────────────────────
# FORMAT: "raw name variant (lowercase)" -> "Canonical Name"
#
# HOW TO EDIT:
#   Add a new row:  "raw variant": "Canonical Name"
#   Key is always lowercase. Value is the display name you want in the output.
#   If unsure, add to UNCERTAIN_ALIASES instead.
#
# IMPORTANT: Never map "java" -> "JavaScript" or vice versa.
# They are distinct technologies with different O*NET/ESCO identifiers.

ALIAS_MAP: dict[str, str] = {
    # ── JavaScript variants ───────────────────────────────────────────────────
    "js":                             "JavaScript",
    "javascript":                     "JavaScript",
    "ecmascript":                     "JavaScript",
    "node.js":                        "Node.js",
    "nodejs":                         "Node.js",
    "react.js":                       "React",
    "reactjs":                        "React",
    "react js":                       "React",
    "vue.js":                         "Vue.js",
    "vuejs":                          "Vue.js",

    # ── SQL variants ─────────────────────────────────────────────────────────
    "structured query language":      "SQL",
    "sql":                            "SQL",
    "mysql":                          "MySQL",
    "my sql":                         "MySQL",
    "microsoft sql server":           "SQL Server",
    "ms sql":                         "SQL Server",
    "ms sql server":                  "SQL Server",
    "postgresql":                     "PostgreSQL",
    "postgres":                       "PostgreSQL",

    # ── Python variants ───────────────────────────────────────────────────────
    "python3":                        "Python",
    "python 3":                       "Python",
    "python programming":             "Python",

    # ── Java variants (KEEP SEPARATE FROM JAVASCRIPT) ────────────────────────
    "java programming":               "Java",
    "java language":                  "Java",
    # NOTE: "java" alone is kept as-is and NOT mapped to JavaScript

    # ── Microsoft Office ──────────────────────────────────────────────────────
    "ms excel":                       "Microsoft Excel",
    "microsoft excel":                "Microsoft Excel",
    "ms word":                        "Microsoft Word",
    "microsoft word":                 "Microsoft Word",
    "ms powerpoint":                  "Microsoft PowerPoint",
    "microsoft powerpoint":           "Microsoft PowerPoint",
    "ms office":                      "Microsoft Office",
    "microsoft office suite":         "Microsoft Office",

    # ── Cloud platforms ───────────────────────────────────────────────────────
    "amazon web services":            "AWS",
    "aws":                            "AWS",
    "google cloud platform":          "Google Cloud",
    "gcp":                            "Google Cloud",
    "azure":                          "Microsoft Azure",
    "ms azure":                       "Microsoft Azure",
    "microsoft azure":                "Microsoft Azure",

    # ── Machine learning / AI ─────────────────────────────────────────────────
    "machine learning":               "Machine Learning",
    "ml":                             "Machine Learning",
    "artificial intelligence":        "Artificial Intelligence",
    "ai":                             "Artificial Intelligence",
    "deep learning":                  "Deep Learning",
    "dl":                             "Deep Learning",
    "natural language processing":    "NLP",
    "nlp":                            "NLP",

    # ── Data tools ────────────────────────────────────────────────────────────
    "pandas library":                 "Pandas",
    "numpy":                          "NumPy",
    "matplotlib":                     "Matplotlib",

    # ── Version control ───────────────────────────────────────────────────────
    "git version control":            "Git",
    "github":                         "GitHub",
    "gitlab":                         "GitLab",

    # ── Communication & soft skills ───────────────────────────────────────────
    "verbal communication":           "Communication",
    "written communication":          "Communication",
    "oral communication":             "Communication",
    "problem-solving":                "Problem Solving",
    "problem solving":                "Problem Solving",
    "critical-thinking":              "Critical Thinking",
    "critical thinking":              "Critical Thinking",
    "team work":                      "Teamwork",
    "team-work":                      "Teamwork",
    "collaboration":                  "Teamwork",
    "time management":                "Time Management",
    "leadership skills":              "Leadership",
}

# ── Uncertain mappings — flagged for manual review ───────────────────────────
# Add here when you are unsure whether two terms truly refer to the same skill.
UNCERTAIN_ALIASES: dict[str, str] = {
    "c#":               "C#",           # Could clash with plain "C" in some systems
    "c sharp":          "C#",
    "objective-c":      "Objective-C",  # Different from C/C++
    "kotlin":           "Kotlin",       # Related to Java but distinct
    "hadoop":           "Apache Hadoop",
    "spark":            "Apache Spark",
}


# ── Normalisation logic ───────────────────────────────────────────────────────

def normalize_skill_name(raw: str) -> tuple[str, str]:
    """
    Look up raw skill name in ALIAS_MAP and UNCERTAIN_ALIASES.

    Returns:
        (normalized_name, flag)
        flag is one of: "OK", "REVIEW_NEEDED", "NO_MATCH"
    """
    if pd.isna(raw) or str(raw).strip() == "":
        return raw, "NO_MATCH"

    key = str(raw).strip().lower()

    if key in ALIAS_MAP:
        return ALIAS_MAP[key], "OK"
    if key in UNCERTAIN_ALIASES:
        return UNCERTAIN_ALIASES[key], "REVIEW_NEEDED"

    # No alias found — return the original (title-cased for consistency)
    return str(raw).strip(), "NO_MATCH"


def normalize_dataframe(df: pd.DataFrame, skill_col: str,
                        id_col: str | None, label: str) -> pd.DataFrame:
    """
    Add normalization columns to a DataFrame.

    Args:
        df        : Input DataFrame
        skill_col : Column containing the raw skill name
        id_col    : Column containing the source identifier (preserved as-is)
        label     : Label for logging

    Adds columns:
        skill_name_original    : copy of original skill_col value
        skill_name_normalized  : result of alias lookup
        normalization_flag     : OK | REVIEW_NEEDED | NO_MATCH
    """
    if skill_col not in df.columns:
        print(f"  [WARN] Column '{skill_col}' not found in {label} — skipping normalization")
        return df

    # Preserve original
    if "skill_name_original" not in df.columns:
        df.insert(
            df.columns.get_loc(skill_col) + 1,
            "skill_name_original",
            df[skill_col]
        )

    results = df[skill_col].apply(normalize_skill_name)
    df["skill_name_normalized"] = [r[0] for r in results]
    df["normalization_flag"]    = [r[1] for r in results]

    ok    = (df["normalization_flag"] == "OK").sum()
    review= (df["normalization_flag"] == "REVIEW_NEEDED").sum()
    no_match = (df["normalization_flag"] == "NO_MATCH").sum()

    print(f"  Normalized '{skill_col}' in {label}:")
    print(f"      Alias matched (OK)    : {ok:,}")
    print(f"      Uncertain (REVIEW)    : {review:,}")
    print(f"      No alias (NO_MATCH)   : {no_match:,}")

    return df


# ── File tasks ────────────────────────────────────────────────────────────────
# (input_csv, output_csv, skill_column, id_column)
# NOTE: All O*NET skill files share the same schema: Element Name + Element ID
# Software Skills also uses Element Name (confirmed from inspection).
# ESCO skills use preferredLabel + conceptUri.
TASKS = [
    (
        "onet_essential_skills_clean.csv",
        "onet_essential_skills_normalized.csv",
        "Element Name",
        "Element ID",
    ),
    (
        "onet_transferable_skills_clean.csv",
        "onet_transferable_skills_normalized.csv",
        "Element Name",
        "Element ID",
    ),
    (
        "onet_knowledge_clean.csv",
        "onet_knowledge_normalized.csv",
        "Element Name",
        "Element ID",
    ),
    (
        "onet_abilities_clean.csv",
        "onet_abilities_normalized.csv",
        "Element Name",
        "Element ID",
    ),
    (
        "onet_software_skills_clean.csv",
        "onet_software_skills_normalized.csv",
        "Element Name",          # Confirmed from inspection: col is 'Element Name'
        "Element ID",
    ),
    (
        "esco_skills_clean.csv",
        "esco_skills_normalized.csv",
        "preferredLabel",
        "conceptUri",
    ),
]


# ── Main ─────────────────────────────────────────────────────────────────────

def run_normalization() -> None:
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print("=" * 70)
    print("  SKILL NORMALISATION PIPELINE")
    print(f"  Started: {timestamp}")
    print(f"  Alias map size: {len(ALIAS_MAP)} entries")
    print(f"  Uncertain map : {len(UNCERTAIN_ALIASES)} entries")
    print("=" * 70)

    all_flagged = []

    for in_csv, out_csv, skill_col, id_col in TASKS:
        in_path  = PROCESSED / in_csv
        out_path = PROCESSED / out_csv

        if not in_path.exists():
            print(f"\n[MISSING] {in_csv} — run clean_data.py first")
            continue

        print(f"\nProcessing: {in_csv}")
        df = pd.read_csv(in_path, encoding="utf-8", low_memory=False)
        print(f"  Loaded: {len(df):,} rows x {df.shape[1]} cols")

        df = normalize_dataframe(df, skill_col, id_col, in_csv)

        # Collect flagged rows for review
        flagged = df[df["normalization_flag"] == "REVIEW_NEEDED"].copy()
        if len(flagged) > 0:
            flagged["source_file"] = in_csv
            all_flagged.append(flagged)

        df.to_csv(out_path, index=False, encoding="utf-8")
        print(f"  Saved : {out_path.name}")

    # Save all review-needed rows to a single file
    if all_flagged:
        flagged_df = pd.concat(all_flagged, ignore_index=True)
        flagged_path = PROCESSED / "normalization_flagged_review.csv"
        flagged_df.to_csv(flagged_path, index=False, encoding="utf-8")
        print(f"\n  Flagged review file: {flagged_path.name} ({len(flagged_df)} rows)")
    else:
        print("\n  No uncertain mappings flagged.")

    print("\n" + "=" * 70)
    print("  NORMALISATION COMPLETE")
    print(f"  Output directory: {PROCESSED}")
    print("=" * 70)


if __name__ == "__main__":
    if not PROCESSED.exists():
        print("ERROR: data/processed/ not found. Run clean_data.py first.")
        sys.exit(1)
    run_normalization()
