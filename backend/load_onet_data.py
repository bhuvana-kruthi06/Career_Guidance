"""
load_onet_data.py
-----------------
Loads O*NET 31.0 data from processed CSV files into MySQL.

PURPOSE:
    Populates two tables that are too large to inline in schema.sql:
      - onet_occupations   : 1,016 rows from career_metadata.csv
      - onet_career_skill_map : 50,261 rows from career_skill_map.csv

PREREQUISITES:
    1. MySQL Server must be running (localhost:3306 by default).
    2. schema.sql must have been applied first:
           mysql -u root -p ai_career_guidance < backend/schema.sql
    3. Install the MySQL connector:
           pip install mysql-connector-python
    4. Create a .env file in the project root (never commit it):
           DB_HOST=localhost
           DB_PORT=3306
           DB_NAME=ai_career_guidance
           DB_USER=root
           DB_PASSWORD=your_password_here

USAGE:
    # From project root with venv active:
    python backend/load_onet_data.py

    # Dry-run (parse and validate CSVs, no DB writes):
    python backend/load_onet_data.py --dry-run

    # Reload only occupations (skip career-skill map):
    python backend/load_onet_data.py --table occupations

    # Reload only career-skill map:
    python backend/load_onet_data.py --table skillmap

SAFETY:
    - Uses INSERT IGNORE so re-running is safe (no duplicate-key errors).
    - Reads DB credentials from .env — never hardcoded.
    - Raw data files are never modified.
    - All row counts are reported before and after insertion.

EXPECTED OUTPUT:
    [INFO] Connecting to MySQL at localhost:3306 / ai_career_guidance ...
    [INFO] Connection OK.

    --- Loading onet_occupations ---
    [INFO] Read 1,016 rows from career_metadata.csv
    [INFO] Inserting in batches of 200 ...
    [INFO] onet_occupations: inserted 1,016 rows  (skipped 0 duplicates)

    --- Loading onet_career_skill_map ---
    [INFO] Read 50,261 rows from career_skill_map.csv
    [INFO] Inserting in batches of 500 ...
    [INFO] onet_career_skill_map: inserted 50,261 rows  (skipped 0 duplicates)

    [SUCCESS] O*NET data load complete.
"""

import argparse
import math
import os
import sys
from pathlib import Path

# ── dependency guard ─────────────────────────────────────────────────────────
try:
    import mysql.connector
    from mysql.connector import Error as MySQLError
except ImportError:
    mysql = None
    MySQLError = Exception

try:
    import pandas as pd
except ImportError:
    sys.exit(
        "[ERROR] pandas is not installed.\n"
        "        Run: pip install pandas"
    )

try:
    from dotenv import load_dotenv
except ImportError:
    sys.exit(
        "[ERROR] python-dotenv is not installed.\n"
        "        Run: pip install python-dotenv"
    )

# ── paths ─────────────────────────────────────────────────────────────────────
PROJECT_ROOT   = Path(__file__).resolve().parent.parent
PROCESSED_DIR  = PROJECT_ROOT / "data" / "processed"

CAREER_METADATA_CSV  = PROCESSED_DIR / "career_metadata.csv"
CAREER_SKILL_MAP_CSV = PROCESSED_DIR / "career_skill_map.csv"

# ── config ────────────────────────────────────────────────────────────────────
BATCH_SIZE_OCCUPATIONS = 200
BATCH_SIZE_SKILLMAP    = 500
DATASET_VERSION        = "31.0"


# ── helpers ───────────────────────────────────────────────────────────────────

def load_env() -> dict:
    """Load DB credentials from project-root .env file."""
    env_path = PROJECT_ROOT / ".env"
    if env_path.exists():
        load_dotenv(env_path)
    else:
        print(
            f"[WARNING] .env not found at {env_path}\n"
            "          Reading credentials from environment variables."
        )

    host     = os.getenv("DB_HOST",     "localhost")
    port     = int(os.getenv("DB_PORT", "3306"))
    name     = os.getenv("DB_NAME",     "ai_career_guidance")
    user     = os.getenv("DB_USER",     "root")
    password = os.getenv("DB_PASSWORD", "")

    if not password:
        print(
            "[WARNING] DB_PASSWORD is empty. "
            "Set it in .env or as an environment variable."
        )

    return {"host": host, "port": port, "database": name,
            "user": user, "password": password}


def get_connection(cfg: dict):
    """Return a MySQL connection. Exits on failure."""
    if mysql is None:
        sys.exit("[ERROR] mysql-connector-python is required for database loads. Install it with: pip install mysql-connector-python")
    print(f"[INFO] Connecting to MySQL at {cfg['host']}:{cfg['port']} "
          f"/ {cfg['database']} ...")
    try:
        conn = mysql.connector.connect(
            host        = cfg["host"],
            port        = cfg["port"],
            database    = cfg["database"],
            user        = cfg["user"],
            password    = cfg["password"],
            charset     = "utf8mb4",
            use_unicode = True,
            autocommit  = False,
        )
        print("[INFO] Connection OK.")
        return conn
    except MySQLError as e:
        sys.exit(f"[ERROR] Cannot connect to MySQL: {e}")


def none_if_nan(val):
    """Convert float NaN (from pandas) to Python None for MySQL NULL."""
    if val is None:
        return None
    try:
        if math.isnan(float(val)):
            return None
    except (TypeError, ValueError):
        pass
    return val


def clean_riasec(val) -> str | None:
    """
    Return val if it is a valid RIASEC letter, else None.
    O*NET uses '?' when a code is not applicable — store as NULL.
    """
    if val is None:
        return None
    s = str(val).strip().upper()
    return s if s in ('R', 'I', 'A', 'S', 'E', 'C') else None


def insert_in_batches(cursor, sql: str, rows: list, batch_size: int,
                      table_name: str, dry_run: bool) -> int:
    """
    Insert rows in batches. Returns total rows affected.
    Uses executemany for efficiency.
    """
    total_affected = 0
    total_batches  = math.ceil(len(rows) / batch_size)
    print(f"[INFO] Inserting {len(rows):,} rows into {table_name} "
          f"in {total_batches} batch(es) of up to {batch_size} ...")

    if dry_run:
        print(f"[DRY-RUN] Would insert {len(rows):,} rows — no DB changes made.")
        return 0

    for i in range(0, len(rows), batch_size):
        batch = rows[i : i + batch_size]
        cursor.executemany(sql, batch)
        total_affected += cursor.rowcount
        batch_num = (i // batch_size) + 1
        if batch_num % 10 == 0 or batch_num == total_batches:
            print(f"  ... batch {batch_num}/{total_batches} done")

    return total_affected


# ── loaders ───────────────────────────────────────────────────────────────────

def load_occupations(conn, dry_run: bool) -> None:
    """Load onet_occupations from career_metadata.csv."""
    print("\n--- Loading onet_occupations ---")

    if not CAREER_METADATA_CSV.exists():
        sys.exit(f"[ERROR] File not found: {CAREER_METADATA_CSV}")

    df = pd.read_csv(CAREER_METADATA_CSV, dtype=str)
    print(f"[INFO] Read {len(df):,} rows from {CAREER_METADATA_CSV.name}")

    required_cols = {"onet_soc_code", "occupation_title"}
    missing = required_cols - set(df.columns)
    if missing:
        sys.exit(f"[ERROR] career_metadata.csv missing columns: {missing}")

    # Build row tuples
    rows = []
    for _, r in df.iterrows():
        job_zone = none_if_nan(r.get("job_zone"))
        if job_zone is not None:
            try:
                jz = int(float(job_zone))
                job_zone = jz if 1 <= jz <= 5 else None
            except (ValueError, TypeError):
                job_zone = None

        rows.append((
            str(r["onet_soc_code"]).strip(),
            str(r["occupation_title"]).strip(),
            none_if_nan(r.get("description")),
            job_zone,
            clean_riasec(r.get("riasec_top1")),
            clean_riasec(r.get("riasec_top2")),
            clean_riasec(r.get("riasec_top3")),
            DATASET_VERSION,
        ))

    sql = """
        INSERT IGNORE INTO onet_occupations
            (onet_soc_code, occupation_title, description,
             job_zone, riasec_top1, riasec_top2, riasec_top3, dataset_version)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
    """

    cursor = conn.cursor()
    affected = insert_in_batches(cursor, sql, rows,
                                 BATCH_SIZE_OCCUPATIONS,
                                 "onet_occupations", dry_run)
    if not dry_run:
        conn.commit()
        skipped = len(rows) - affected
        print(f"[INFO] onet_occupations: inserted {affected:,} rows  "
              f"(skipped {skipped:,} duplicates)")
    cursor.close()


def load_career_skill_map(conn, dry_run: bool) -> None:
    """Load onet_career_skill_map from career_skill_map.csv."""
    print("\n--- Loading onet_career_skill_map ---")

    if not CAREER_SKILL_MAP_CSV.exists():
        sys.exit(f"[ERROR] File not found: {CAREER_SKILL_MAP_CSV}")

    df = pd.read_csv(CAREER_SKILL_MAP_CSV, dtype=str)
    print(f"[INFO] Read {len(df):,} rows from {CAREER_SKILL_MAP_CSV.name}")

    required_cols = {"onet_soc_code", "element_id", "importance_score"}
    missing = required_cols - set(df.columns)
    if missing:
        sys.exit(f"[ERROR] career_skill_map.csv missing columns: {missing}")

    # Build row tuples
    rows = []
    skipped_invalid = 0
    for _, r in df.iterrows():
        try:
            importance = float(r["importance_score"])
        except (ValueError, TypeError):
            skipped_invalid += 1
            continue  # skip rows with unparseable importance

        level_raw = r.get("level_score")
        try:
            level = float(level_raw) if level_raw and str(level_raw).strip() else None
        except (ValueError, TypeError):
            level = None

        rows.append((
            str(r["onet_soc_code"]).strip(),
            str(r["element_id"]).strip(),
            round(importance, 2),
            round(level, 2) if level is not None else None,
            DATASET_VERSION,
        ))

    if skipped_invalid:
        print(f"[WARNING] Skipped {skipped_invalid} rows with invalid importance_score.")

    sql = """
        INSERT IGNORE INTO onet_career_skill_map
            (onet_soc_code, element_id, importance_score, level_score, dataset_version)
        VALUES (%s, %s, %s, %s, %s)
    """

    cursor = conn.cursor()
    affected = insert_in_batches(cursor, sql, rows,
                                 BATCH_SIZE_SKILLMAP,
                                 "onet_career_skill_map", dry_run)
    if not dry_run:
        conn.commit()
        skipped_dup = len(rows) - affected
        print(f"[INFO] onet_career_skill_map: inserted {affected:,} rows  "
              f"(skipped {skipped_dup:,} duplicates)")
    cursor.close()


def verify_counts(conn, expected_counts: dict[str, int]) -> bool:
    """Print row counts for all three O*NET tables after loading."""
    print("\n--- Verification: Row Counts ---")
    tables = ["onet_occupations", "onet_skill_elements", "onet_career_skill_map"]
    cursor = conn.cursor()
    all_match = True
    for table in tables:
        cursor.execute(f"SELECT COUNT(*) FROM {table}")
        count = cursor.fetchone()[0]
        expected = expected_counts.get(table)
        matches = expected is None or count == expected
        all_match = all_match and matches
        status = "OK" if matches else "COUNT MISMATCH"
        expected_label = f"source: {expected:,}" if expected is not None else "schema seed"
        print(f"  [{status}] {table:<30} {count:>7,} rows   ({expected_label})")
    cursor.close()
    return all_match


# ── CLI ───────────────────────────────────────────────────────────────────────

def parse_args():
    parser = argparse.ArgumentParser(
        description="Load O*NET 31.0 data into MySQL ai_career_guidance database."
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Parse CSVs and validate rows but make no DB changes.",
    )
    parser.add_argument(
        "--table",
        choices=["occupations", "skillmap", "all"],
        default="all",
        help="Which table to load (default: all).",
    )
    return parser.parse_args()


def validate_csv_inputs() -> None:
    """Validate source files without requiring a running MySQL server."""
    requirements = (
        (CAREER_METADATA_CSV, {"onet_soc_code", "occupation_title"}, 1_016),
        (CAREER_SKILL_MAP_CSV, {"onet_soc_code", "element_id", "importance_score"}, 50_261),
    )
    for path, required, expected_rows in requirements:
        if not path.exists():
            sys.exit(f"[ERROR] File not found: {path}")
        frame = pd.read_csv(path, dtype=str)
        missing = required - set(frame.columns)
        if missing:
            sys.exit(f"[ERROR] {path.name} missing columns: {sorted(missing)}")
        if frame.empty:
            sys.exit(f"[ERROR] {path.name} has no data rows")
        print(f"[DRY-RUN] {path.name}: {len(frame):,} rows; required columns present (expected {expected_rows:,}).")
    skills = pd.read_csv(CAREER_SKILL_MAP_CSV, usecols=["element_id"], dtype=str)
    print(f"[DRY-RUN] Career map contains {skills['element_id'].nunique():,} unique skill element IDs.")


def main():
    args = parse_args()

    if args.dry_run:
        print("[DRY-RUN MODE] No database changes will be made.")
        validate_csv_inputs()
        print("[SUCCESS] Source CSV validation complete; no database connection was opened.")
        return

    cfg  = load_env()
    conn = None

    try:
        conn = get_connection(cfg)

        source_counts = {}
        if args.table in ("occupations", "all"):
            source_counts["onet_occupations"] = len(pd.read_csv(CAREER_METADATA_CSV, dtype=str))
            load_occupations(conn, args.dry_run)

        if args.table in ("skillmap", "all"):
            source_counts["onet_career_skill_map"] = len(pd.read_csv(CAREER_SKILL_MAP_CSV, dtype=str))
            load_career_skill_map(conn, args.dry_run)

        if not args.dry_run:
            counts_match = verify_counts(conn, source_counts)
            if not counts_match:
                sys.exit("[ERROR] Database table counts do not match the source CSV row counts.")

        print("\n[SUCCESS] O*NET data load complete.")

    except MySQLError as e:
        print(f"\n[ERROR] MySQL error during load: {e}")
        if conn:
            conn.rollback()
        sys.exit(1)

    except KeyboardInterrupt:
        print("\n[INTERRUPTED] Load cancelled by user.")
        if conn:
            conn.rollback()
        sys.exit(1)

    finally:
        if conn and conn.is_connected():
            conn.close()
            print("[INFO] Database connection closed.")


if __name__ == "__main__":
    main()
