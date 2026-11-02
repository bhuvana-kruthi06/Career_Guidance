"""
inspect_data.py
---------------
Step 1 of the AI Career Guidance data pipeline.

PURPOSE:
    Reads the raw O*NET and ESCO datasets and produces a structured
    inspection report. This script is READ-ONLY — it never modifies
    source files or writes to data/raw/.

OUTPUTS:
    - Console summary for each dataset file
    - reports/data_inspection_report.txt  (plain-text log)

DATASETS INSPECTED:
    - O*NET 31.0 (.xlsx files in data/raw/onet/)
    - ESCO v1.2.1 (.csv files in data/raw/esco/)

ATTRIBUTION:
    O*NET: U.S. Department of Labor, CC BY 4.0, https://www.onetcenter.org
    ESCO:  European Commission, CC BY 4.0, https://esco.ec.europa.eu

USAGE:
    Activate venv first:  venv\\Scripts\\activate
    Then run:             python data_processing/inspect_data.py
"""

import os
import sys
import pandas as pd
from datetime import datetime
from pathlib import Path

# ── Paths ────────────────────────────────────────────────────────────────────
PROJECT_ROOT = Path(__file__).resolve().parent.parent
ONET_DIR     = PROJECT_ROOT / "data" / "raw" / "onet"
ESCO_DIR     = PROJECT_ROOT / "data" / "raw" / "esco"
REPORTS_DIR  = PROJECT_ROOT / "reports"
REPORTS_DIR.mkdir(parents=True, exist_ok=True)

REPORT_FILE = REPORTS_DIR / "data_inspection_report.txt"

# ── O*NET files to inspect (priority files first) ────────────────────────────
ONET_FILES = [
    "Occupation Data.xlsx",
    "Skills.xlsx",
    "Knowledge.xlsx",
    "Abilities.xlsx",
    "Essential Skills.xlsx",
    "Transferable Skills.xlsx",
    "Task Statements.xlsx",
    "Education.xlsx",
    "Job Zones.xlsx",
    "Content Model Reference.xlsx",
    "Software Skills.xlsx",
    "Related Occupations.xlsx",
    "Career Interest Types.xlsx",
    "Scales Reference.xlsx",
]

# ── ESCO files to inspect ────────────────────────────────────────────────────
ESCO_FILES = [
    "occupations_en.csv",
    "skills_en.csv",
    "occupationSkillRelations_en.csv",
    "skillsHierarchy_en.csv",
    "skillGroups_en.csv",
    "broaderRelationsSkillPillar_en.csv",
    "skillSkillRelations_en.csv",
    "ISCOGroups_en.csv",
    "digitalSkillsCollection_en.csv",
    "transversalSkillsCollection_en.csv",
]


# ── Helpers ──────────────────────────────────────────────────────────────────

def separator(title: str, char: str = "=", width: int = 70) -> str:
    return f"\n{char * width}\n  {title}\n{char * width}"


def inspect_dataframe(df: pd.DataFrame, file_label: str, source: str) -> str:
    """
    Generate a structured inspection report for a single DataFrame.
    Returns a plain-text string.
    """
    lines = []
    lines.append(f"\nFILE : {file_label}")
    lines.append(f"SOURCE: {source}")
    lines.append(f"  Rows             : {df.shape[0]:,}")
    lines.append(f"  Columns          : {df.shape[1]}")
    lines.append(f"  Column names     : {list(df.columns)}")
    lines.append(f"  Data types:")
    for col, dtype in df.dtypes.items():
        lines.append(f"      {col:<45} {str(dtype)}")

    # Missing values
    missing = df.isnull().sum()
    missing_pct = (missing / len(df) * 100).round(2)
    has_missing = missing[missing > 0]
    if has_missing.empty:
        lines.append("  Missing values   : None")
    else:
        lines.append(f"  Missing values   : {len(has_missing)} column(s) have nulls")
        for col in has_missing.index:
            lines.append(f"      {col:<45} {missing[col]:,} ({missing_pct[col]}%)")

    # Duplicates
    dupe_count = df.duplicated().sum()
    lines.append(f"  Duplicate rows   : {dupe_count:,}")

    # Unique counts for key identifier columns
    id_like = [c for c in df.columns
               if any(k in c.lower() for k in
                      ["code", "id", "uri", "concept", "isco", "soc"])]
    if id_like:
        lines.append(f"  Identifier columns detected:")
        for col in id_like[:5]:          # cap at 5 to keep output readable
            lines.append(f"      {col:<45} {df[col].nunique():,} unique values")

    # Sample rows (first 3)
    lines.append("  Sample rows (first 3):")
    for _, row in df.head(3).iterrows():
        row_str = " | ".join(f"{k}: {str(v)[:40]}" for k, v in row.items())
        lines.append(f"      {row_str}")

    return "\n".join(lines)


def load_onet_file(filepath: Path) -> pd.DataFrame | None:
    """Load an O*NET .xlsx file. Returns None if the file is missing."""
    if not filepath.exists():
        print(f"  [MISSING] {filepath.name}")
        return None
    try:
        df = pd.read_excel(filepath, engine="openpyxl")
        return df
    except Exception as e:
        print(f"  [ERROR reading {filepath.name}] {e}")
        return None


def load_esco_file(filepath: Path) -> pd.DataFrame | None:
    """Load an ESCO .csv file. Returns None if the file is missing."""
    if not filepath.exists():
        print(f"  [MISSING] {filepath.name}")
        return None
    try:
        df = pd.read_csv(filepath, encoding="utf-8", low_memory=False)
        return df
    except Exception as e:
        print(f"  [ERROR reading {filepath.name}] {e}")
        return None


# ── Main inspection logic ─────────────────────────────────────────────────────

def run_inspection() -> None:
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    all_lines = []

    header = [
        separator("DATA INSPECTION REPORT", "="),
        f"Project  : AI-Powered Personalised Career Guidance Platform",
        f"Generated: {timestamp}",
        f"Script   : data_processing/inspect_data.py",
        f"Mode     : READ-ONLY — source files are never modified",
        "",
        "Attribution:",
        "  O*NET 31.0  — U.S. Dept of Labor, CC BY 4.0 — https://www.onetcenter.org",
        "  ESCO v1.2.1 — European Commission, CC BY 4.0 — https://esco.ec.europa.eu",
    ]
    all_lines.extend(header)

    # ── O*NET ────────────────────────────────────────────────────────────────
    all_lines.append(separator("O*NET 31.0 DATASET", "-"))
    all_lines.append(f"Directory: {ONET_DIR}")
    all_lines.append(f"Files to inspect: {len(ONET_FILES)}")

    onet_results = {}
    for fname in ONET_FILES:
        fpath = ONET_DIR / fname
        print(f"Inspecting O*NET: {fname} ...", end=" ", flush=True)
        df = load_onet_file(fpath)
        if df is not None:
            print(f"{df.shape[0]:,} rows x {df.shape[1]} cols")
            report = inspect_dataframe(df, fname, "O*NET 31.0")
            all_lines.append(report)
            onet_results[fname] = {"rows": df.shape[0], "cols": df.shape[1], "status": "OK"}
        else:
            onet_results[fname] = {"status": "MISSING or ERROR"}

    # ── ESCO ─────────────────────────────────────────────────────────────────
    all_lines.append(separator("ESCO v1.2.1 DATASET", "-"))
    all_lines.append(f"Directory: {ESCO_DIR}")
    all_lines.append(f"Files to inspect: {len(ESCO_FILES)}")

    esco_results = {}
    for fname in ESCO_FILES:
        fpath = ESCO_DIR / fname
        print(f"Inspecting ESCO:  {fname} ...", end=" ", flush=True)
        df = load_esco_file(fpath)
        if df is not None:
            print(f"{df.shape[0]:,} rows x {df.shape[1]} cols")
            report = inspect_dataframe(df, fname, "ESCO v1.2.1")
            all_lines.append(report)
            esco_results[fname] = {"rows": df.shape[0], "cols": df.shape[1], "status": "OK"}
        else:
            esco_results[fname] = {"status": "MISSING or ERROR"}

    # ── Summary table ─────────────────────────────────────────────────────────
    all_lines.append(separator("INSPECTION SUMMARY", "="))
    all_lines.append("\nO*NET files:")
    all_lines.append(f"  {'File':<50} {'Rows':>10}  {'Cols':>5}  Status")
    all_lines.append(f"  {'-'*50} {'-'*10}  {'-'*5}  ------")
    for fname, info in onet_results.items():
        if info["status"] == "OK":
            all_lines.append(
                f"  {fname:<50} {info['rows']:>10,}  {info['cols']:>5}  OK"
            )
        else:
            all_lines.append(f"  {fname:<50} {'—':>10}  {'—':>5}  {info['status']}")

    all_lines.append("\nESCO files:")
    all_lines.append(f"  {'File':<55} {'Rows':>10}  {'Cols':>5}  Status")
    all_lines.append(f"  {'-'*55} {'-'*10}  {'-'*5}  ------")
    for fname, info in esco_results.items():
        if info["status"] == "OK":
            all_lines.append(
                f"  {fname:<55} {info['rows']:>10,}  {info['cols']:>5}  OK"
            )
        else:
            all_lines.append(f"  {fname:<55} {'—':>10}  {'—':>5}  {info['status']}")

    all_lines.append(f"\nReport saved to: {REPORT_FILE}")

    # ── Write report ──────────────────────────────────────────────────────────
    full_report = "\n".join(all_lines)
    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        f.write(full_report)

    print("\n" + "=" * 70)
    print("  INSPECTION COMPLETE")
    print(f"  Report saved: {REPORT_FILE}")
    print("=" * 70)


if __name__ == "__main__":
    # Confirm dataset directories exist before starting
    missing_dirs = []
    if not ONET_DIR.exists():
        missing_dirs.append(str(ONET_DIR))
    if not ESCO_DIR.exists():
        missing_dirs.append(str(ESCO_DIR))

    if missing_dirs:
        print("ERROR: The following dataset directories are missing:")
        for d in missing_dirs:
            print(f"  {d}")
        print("Please place dataset files in the correct folders and re-run.")
        sys.exit(1)

    run_inspection()
