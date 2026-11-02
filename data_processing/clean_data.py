"""
clean_data.py
-------------
Step 2 of the AI Career Guidance data pipeline.

PURPOSE:
    Reads the raw O*NET and ESCO files, applies documented cleaning rules,
    and writes cleaned outputs to data/processed/.

    Raw files in data/raw/ are NEVER modified.

CLEANING RULES APPLIED:
    1. Strip leading/trailing whitespace from all string columns.
    2. Standardise line endings within cells (replace \\r\\n and \\n with space).
    3. Drop fully empty rows (all columns null).
    4. Remove exact duplicate rows (all columns identical).
    5. Preserve original O*NET SOC codes and ESCO concept URIs unchanged.
    6. Preserve original skill names in a '_original' suffix column before
       any transformation.
    7. Report row counts before and after each cleaning step.

OUTPUTS:
    data/processed/onet_occupation_data_clean.csv
    data/processed/onet_skills_clean.csv
    data/processed/onet_knowledge_clean.csv
    data/processed/onet_abilities_clean.csv
    data/processed/onet_essential_skills_clean.csv
    data/processed/onet_software_skills_clean.csv
    data/processed/esco_occupations_clean.csv
    data/processed/esco_skills_clean.csv
    data/processed/esco_occupation_skill_relations_clean.csv

USAGE:
    Activate venv first:  venv\\Scripts\\activate
    Then run:             python data_processing/clean_data.py
"""

import os
import sys
import pandas as pd
from pathlib import Path
from datetime import datetime

# ── Paths ────────────────────────────────────────────────────────────────────
PROJECT_ROOT = Path(__file__).resolve().parent.parent
ONET_DIR     = PROJECT_ROOT / "data" / "raw" / "onet"
ESCO_DIR     = PROJECT_ROOT / "data" / "raw" / "esco"
PROCESSED    = PROJECT_ROOT / "data" / "processed"
PROCESSED.mkdir(parents=True, exist_ok=True)

# ── Files to clean ────────────────────────────────────────────────────────────
# Format: (source_dir, input_filename, output_filename, loader)
ONET_TASKS = [
    # NOTE: O*NET 31.0 Excel format has NO generic "Skills.xlsx".
    # Skills are split across Essential Skills, Transferable Skills, Knowledge, Abilities.
    ("onet", "Occupation Data.xlsx",     "onet_occupation_data_clean.csv",      "xlsx"),
    ("onet", "Essential Skills.xlsx",    "onet_essential_skills_clean.csv",     "xlsx"),
    ("onet", "Transferable Skills.xlsx", "onet_transferable_skills_clean.csv",  "xlsx"),
    ("onet", "Knowledge.xlsx",           "onet_knowledge_clean.csv",            "xlsx"),
    ("onet", "Abilities.xlsx",           "onet_abilities_clean.csv",            "xlsx"),
    ("onet", "Software Skills.xlsx",     "onet_software_skills_clean.csv",      "xlsx"),
    ("onet", "Education.xlsx",           "onet_education_clean.csv",            "xlsx"),
    ("onet", "Job Zones.xlsx",           "onet_job_zones_clean.csv",            "xlsx"),
    ("onet", "Career Interest Types.xlsx","onet_career_interests_clean.csv",    "xlsx"),
    ("onet", "Related Occupations.xlsx", "onet_related_occupations_clean.csv",  "xlsx"),
]
ESCO_TASKS = [
    ("esco", "occupations_en.csv",              "esco_occupations_clean.csv",              "csv"),
    ("esco", "skills_en.csv",                   "esco_skills_clean.csv",                   "csv"),
    ("esco", "occupationSkillRelations_en.csv", "esco_occupation_skill_relations_clean.csv","csv"),
    ("esco", "skillGroups_en.csv",              "esco_skill_groups_clean.csv",             "csv"),
    ("esco", "ISCOGroups_en.csv",               "esco_isco_groups_clean.csv",              "csv"),
]

ALL_TASKS = ONET_TASKS + ESCO_TASKS


# ── Cleaning functions ────────────────────────────────────────────────────────

def clean_whitespace(df: pd.DataFrame) -> pd.DataFrame:
    """
    Strip leading/trailing whitespace from all string columns.
    Normalise internal line endings within cells.
    """
    str_cols = df.select_dtypes(include=["object", "str"]).columns
    for col in str_cols:
        df[col] = (
            df[col]
            .astype(str)
            .str.replace(r"\r\n|\r|\n", " ", regex=True)   # flatten multiline cells
            .str.strip()
            .replace("nan", pd.NA)                          # undo str(NaN) artefacts
        )
    return df


def drop_empty_rows(df: pd.DataFrame) -> pd.DataFrame:
    """Remove rows where every column is null/empty."""
    return df.dropna(how="all")


def drop_duplicates(df: pd.DataFrame) -> pd.DataFrame:
    """Remove exact duplicate rows (all columns identical)."""
    return df.drop_duplicates()


def preserve_name_original(df: pd.DataFrame, name_col: str) -> pd.DataFrame:
    """
    Add a '<name_col>_original' column preserving the raw value
    before any normalisation step that may overwrite <name_col>.
    Only adds the column if it doesn't already exist.
    """
    original_col = f"{name_col}_original"
    if name_col in df.columns and original_col not in df.columns:
        # Insert original column immediately after the source column
        idx = df.columns.get_loc(name_col) + 1
        df.insert(idx, original_col, df[name_col])
    return df


def clean_dataframe(df: pd.DataFrame, label: str) -> tuple[pd.DataFrame, dict]:
    """
    Apply all cleaning rules in order.
    Returns (cleaned_df, change_log_dict).
    """
    log = {
        "label": label,
        "rows_before": len(df),
        "cols_before":  df.shape[1],
    }

    # Step 1: strip whitespace and flatten line endings
    df = clean_whitespace(df)
    log["after_whitespace_clean"] = len(df)

    # Step 2: drop fully empty rows
    before = len(df)
    df = drop_empty_rows(df)
    log["rows_dropped_empty"] = before - len(df)

    # Step 3: drop exact duplicates
    before = len(df)
    df = drop_duplicates(df)
    log["rows_dropped_duplicates"] = before - len(df)

    # Step 4: preserve original skill/title name columns
    for candidate in ["Element Name", "Title", "preferredLabel", "altLabels",
                       "Workplace Example", "occupationLabel", "skillLabel"]:
        df = preserve_name_original(df, candidate)

    log["rows_after"] = len(df)
    log["cols_after"]  = df.shape[1]
    log["rows_removed_total"] = log["rows_before"] - log["rows_after"]
    return df, log


# ── Loader helpers ────────────────────────────────────────────────────────────

def load_file(src_dir: str, filename: str, loader: str) -> pd.DataFrame | None:
    base = ONET_DIR if src_dir == "onet" else ESCO_DIR
    fpath = base / filename
    if not fpath.exists():
        print(f"  [MISSING] {fpath}")
        return None
    try:
        if loader == "xlsx":
            return pd.read_excel(fpath, engine="openpyxl")
        else:
            return pd.read_csv(fpath, encoding="utf-8", low_memory=False)
    except Exception as e:
        print(f"  [ERROR] Could not read {filename}: {e}")
        return None


# ── Main ─────────────────────────────────────────────────────────────────────

def run_cleaning() -> None:
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print("=" * 70)
    print("  DATA CLEANING PIPELINE")
    print(f"  Started: {timestamp}")
    print("  Mode   : Reads from data/raw/  — Writes to data/processed/")
    print("=" * 70)

    all_logs = []
    success_count = 0
    fail_count = 0

    for src_dir, in_file, out_file, loader in ALL_TASKS:
        print(f"\nProcessing: {in_file}")
        df = load_file(src_dir, in_file, loader)
        if df is None:
            fail_count += 1
            continue

        print(f"  Loaded   : {len(df):,} rows x {df.shape[1]} cols")
        df_clean, log = clean_dataframe(df, in_file)
        print(f"  After    : {log['rows_after']:,} rows x {log['cols_after']} cols")
        print(f"  Removed  : {log['rows_removed_total']} rows "
              f"({log['rows_dropped_empty']} empty + "
              f"{log['rows_dropped_duplicates']} duplicates)")

        out_path = PROCESSED / out_file
        df_clean.to_csv(out_path, index=False, encoding="utf-8")
        print(f"  Saved    : {out_path.name}")
        all_logs.append(log)
        success_count += 1

    # ── Print change summary ──────────────────────────────────────────────────
    print("\n" + "=" * 70)
    print("  CLEANING SUMMARY")
    print(f"  {'File':<50} {'Before':>8}  {'After':>8}  {'Removed':>8}")
    print(f"  {'-'*50} {'-'*8}  {'-'*8}  {'-'*8}")
    for log in all_logs:
        print(f"  {log['label']:<50} "
              f"{log['rows_before']:>8,}  "
              f"{log['rows_after']:>8,}  "
              f"{log['rows_removed_total']:>8,}")
    print(f"\n  Files processed successfully: {success_count}")
    print(f"  Files failed / missing      : {fail_count}")
    print(f"\n  Output directory: {PROCESSED}")
    print("=" * 70)


if __name__ == "__main__":
    if not ONET_DIR.exists() or not ESCO_DIR.exists():
        print("ERROR: data/raw/onet/ or data/raw/esco/ not found.")
        print("Run inspect_data.py first to confirm datasets are present.")
        sys.exit(1)
    run_cleaning()
