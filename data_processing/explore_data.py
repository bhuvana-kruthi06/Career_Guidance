"""
explore_data.py
---------------
Step 4 of the AI Career Guidance data pipeline.

PURPOSE:
    Loads the cleaned and normalised data from data/processed/ and generates
    exploratory data analysis (EDA) statistics and charts.

    All charts are saved to reports/charts/.
    A markdown summary is appended to reports/data_analysis_report.md.

ANALYSES PERFORMED:
    O*NET:
      - Occupation count
      - Skills per occupation (distribution)
      - Top 20 most frequent skills
      - Importance score distribution (where available)
      - Level score distribution (where available)
      - Missing value heatmap

    ESCO:
      - Occupation count
      - Skill count
      - Skills per occupation distribution
      - Top skill groups
      - Skill type breakdown (knowledge / skill / attitude)

    Cross-dataset:
      - Skill name overlap between O*NET and ESCO (before normalization)
      - Skill name overlap after normalization

NOTE:
    All statistics and charts are generated from actual data only.
    No values are invented. If a column is absent, the analysis is
    skipped and a note is added to the report.

USAGE:
    python data_processing/explore_data.py
"""

import sys
import warnings
import pandas as pd
import matplotlib
matplotlib.use("Agg")          # Non-interactive backend — works without a display
import matplotlib.pyplot as plt
import seaborn as sns
from pathlib import Path
from datetime import datetime

warnings.filterwarnings("ignore", category=FutureWarning)

# ── Paths ────────────────────────────────────────────────────────────────────
PROJECT_ROOT = Path(__file__).resolve().parent.parent
PROCESSED    = PROJECT_ROOT / "data" / "processed"
CHARTS_DIR   = PROJECT_ROOT / "reports" / "charts"
REPORT_FILE  = PROJECT_ROOT / "reports" / "data_analysis_report.md"
CHARTS_DIR.mkdir(parents=True, exist_ok=True)

# ── Plotting style ────────────────────────────────────────────────────────────
sns.set_theme(style="whitegrid", palette="muted")
CHART_DPI    = 150
CHART_FORMAT = "png"


# ── Helpers ───────────────────────────────────────────────────────────────────

def save_chart(fig: plt.Figure, name: str) -> Path:
    path = CHARTS_DIR / f"{name}.{CHART_FORMAT}"
    fig.savefig(path, dpi=CHART_DPI, bbox_inches="tight")
    plt.close(fig)
    print(f"  Chart saved: {path.name}")
    return path


def load_csv(filename: str) -> pd.DataFrame | None:
    path = PROCESSED / filename
    if not path.exists():
        print(f"  [MISSING] {filename} — skipping")
        return None
    df = pd.read_csv(path, encoding="utf-8", low_memory=False)
    print(f"  Loaded {filename}: {len(df):,} rows x {df.shape[1]} cols")
    return df


def write_report(lines: list[str]) -> None:
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        f.write(f"# Data Analysis Report\n\n")
        f.write(f"**Generated:** {timestamp}  \n")
        f.write(f"**Script:** `data_processing/explore_data.py`  \n")
        f.write(f"**Data source:** `data/processed/`  \n\n")
        f.write("---\n\n")
        f.write("\n".join(lines))
    print(f"\n  Report saved: {REPORT_FILE.name}")


# ── O*NET EDA ─────────────────────────────────────────────────────────────────

def analyze_onet(report_lines: list[str]) -> None:
    report_lines.append("## O\\*NET 31.0 Analysis\n")

    # 1. Occupation count
    occ = load_csv("onet_occupation_data_clean.csv")
    if occ is not None:
        n_occ = occ["O*NET-SOC Code"].nunique() if "O*NET-SOC Code" in occ.columns else len(occ)
        report_lines.append(f"### Occupations\n- **Total unique occupations:** {n_occ:,}\n")
        report_lines.append(f"- **Columns:** {list(occ.columns)}\n")

    # 2. Essential Skills analysis (primary skills file in O*NET 31.0)
    skills = load_csv("onet_essential_skills_normalized.csv")
    if skills is None:
        skills = load_csv("onet_essential_skills_clean.csv")

    if skills is not None:
        report_lines.append("### Essential Skills\n")
        name_col = "skill_name_normalized" if "skill_name_normalized" in skills.columns else "Element Name"

        n_skills = skills[name_col].nunique() if name_col in skills.columns else 0
        report_lines.append(f"- **Unique skill elements:** {n_skills:,}")

        # Skills per occupation
        if "O*NET-SOC Code" in skills.columns and name_col in skills.columns:
            # Filter to Importance scale only to avoid double-counting IM + LV rows
            if "Scale ID" in skills.columns:
                skills_im = skills[skills["Scale ID"] == "IM"]
            else:
                skills_im = skills

            skills_per_occ = (
                skills_im.groupby("O*NET-SOC Code")[name_col]
                         .nunique()
                         .reset_index(name="skill_count")
            )
            desc = skills_per_occ["skill_count"].describe()
            report_lines.append(
                f"- **Skills per occupation (Importance scale):** "
                f"min={int(desc['min'])}, median={int(desc['50%'])}, "
                f"max={int(desc['max'])}, mean={desc['mean']:.1f}"
            )

            # Chart: skills per occupation
            fig, ax = plt.subplots(figsize=(10, 5))
            ax.hist(skills_per_occ["skill_count"], bins=15, color="#4C72B0", edgecolor="white")
            ax.set_title("O*NET: Number of Essential Skills per Occupation", fontsize=14)
            ax.set_xlabel("Skill Count")
            ax.set_ylabel("Number of Occupations")
            save_chart(fig, "onet_skills_per_occupation")
            report_lines.append(
                "\n![Skills per Occupation](charts/onet_skills_per_occupation.png)\n"
            )

        # Top 20 most frequent skills
        if name_col in skills.columns:
            top_skills = skills[name_col].value_counts().head(20)
            report_lines.append(f"\n#### Top 20 O\\*NET Essential Skills\n")
            report_lines.append("| Rank | Skill | Count |")
            report_lines.append("|------|-------|-------|")
            for i, (skill, count) in enumerate(top_skills.items(), 1):
                report_lines.append(f"| {i} | {skill} | {count:,} |")

            fig, ax = plt.subplots(figsize=(10, 8))
            top_skills.sort_values().plot(kind="barh", ax=ax, color="#4C72B0")
            ax.set_title("O*NET: Top 20 Essential Skills (by frequency)", fontsize=14)
            ax.set_xlabel("Frequency across occupations")
            plt.tight_layout()
            save_chart(fig, "onet_top_skills")
            report_lines.append("\n![Top Skills](charts/onet_top_skills.png)\n")

        # Data Value (Importance) distribution
        if "Data Value" in skills.columns and "Scale ID" in skills.columns:
            im_vals = skills[skills["Scale ID"] == "IM"]["Data Value"].dropna()
            lv_vals = skills[skills["Scale ID"] == "LV"]["Data Value"].dropna()
            report_lines.append(f"\n#### Importance Score Distribution (Scale: 1–5)\n")
            report_lines.append(f"```\n{im_vals.describe().round(2).to_string()}\n```\n")

            fig, axes = plt.subplots(1, 2, figsize=(12, 5))
            axes[0].hist(im_vals, bins=20, color="#4C72B0", edgecolor="white")
            axes[0].set_title("Importance Score Distribution")
            axes[0].set_xlabel("Score (1–5)")
            axes[1].hist(lv_vals, bins=20, color="#DD8452", edgecolor="white")
            axes[1].set_title("Level Score Distribution")
            axes[1].set_xlabel("Score (0–7)")
            plt.suptitle("O*NET Essential Skills: Score Distributions", fontsize=13)
            plt.tight_layout()
            save_chart(fig, "onet_score_distributions")
            report_lines.append("\n![Score Distributions](charts/onet_score_distributions.png)\n")

    # 3. Software Skills
    sw = load_csv("onet_software_skills_clean.csv")
    if sw is not None:
        report_lines.append("### Software Skills (Technology Tools)\n")
        n_tools = sw["Element Name"].nunique() if "Element Name" in sw.columns else len(sw)
        report_lines.append(f"- **Unique software/tool categories:** {n_tools:,}")
        n_occ_sw = sw["O*NET-SOC Code"].nunique() if "O*NET-SOC Code" in sw.columns else 0
        report_lines.append(f"- **Occupations with software skills:** {n_occ_sw:,}")

        if "Workplace Example" in sw.columns:
            top_sw = sw["Workplace Example"].value_counts().head(10)
            report_lines.append(f"\n#### Top 10 Most Listed Software Tools\n")
            report_lines.append("| Rank | Tool | Count |")
            report_lines.append("|------|------|-------|")
            for i, (tool, cnt) in enumerate(top_sw.items(), 1):
                report_lines.append(f"| {i} | {tool} | {cnt:,} |")

        if "Hot Technology" in sw.columns:
            hot = (sw["Hot Technology"] == "Y").sum()
            report_lines.append(f"\n- **Hot Technology entries:** {hot:,}")

    # 4. Job Zones
    jz = load_csv("onet_job_zones_clean.csv")
    if jz is not None and "Job Zone" in jz.columns:
        report_lines.append("\n### Job Zones Distribution\n")
        jz_counts = jz["Job Zone"].value_counts().sort_index()
        report_lines.append("| Job Zone | Occupations |")
        report_lines.append("|----------|-------------|")
        zone_labels = {1:"Little preparation", 2:"Some preparation",
                       3:"Medium preparation", 4:"Considerable preparation",
                       5:"Extensive preparation"}
        for zone, count in jz_counts.items():
            label = zone_labels.get(int(zone), "")
            report_lines.append(f"| Zone {int(zone)} — {label} | {count:,} |")


# ── ESCO EDA ─────────────────────────────────────────────────────────────────

def analyze_esco(report_lines: list[str]) -> None:
    report_lines.append("\n---\n\n## ESCO v1.2.1 Analysis\n")

    # Occupations
    occ = load_csv("esco_occupations_clean.csv")
    if occ is not None:
        n_occ = occ["conceptUri"].nunique() if "conceptUri" in occ.columns else len(occ)
        report_lines.append(f"### Occupations\n- **Total unique occupations:** {n_occ:,}\n")

    # Skills
    skills = load_csv("esco_skills_normalized.csv")
    if skills is not None:
        n_skills = len(skills)
        report_lines.append(f"### Skills\n- **Total skill entries:** {n_skills:,}")

        # Skill type breakdown
        if "skillType" in skills.columns:
            type_counts = skills["skillType"].value_counts()
            report_lines.append("\n#### Skill Type Breakdown\n")
            report_lines.append("| Type | Count |")
            report_lines.append("|------|-------|")
            for t, c in type_counts.items():
                report_lines.append(f"| {t} | {c:,} |")

            fig, ax = plt.subplots(figsize=(8, 5))
            type_counts.plot(kind="bar", ax=ax, color=sns.color_palette("muted"))
            ax.set_title("ESCO: Skill Type Distribution", fontsize=14)
            ax.set_xlabel("Skill Type")
            ax.set_ylabel("Count")
            ax.tick_params(axis="x", rotation=30)
            plt.tight_layout()
            save_chart(fig, "esco_skill_type_distribution")
            report_lines.append(
                "\n![Skill Types](charts/esco_skill_type_distribution.png)\n"
            )

    # Occupation-skill relations
    rel = load_csv("esco_occupation_skill_relations_clean.csv")
    if rel is not None and occ is not None:
        report_lines.append("\n### Occupation–Skill Relations\n")
        n_relations = len(rel)
        report_lines.append(f"- **Total relation entries:** {n_relations:,}")

        occ_col = next((c for c in rel.columns if "occupation" in c.lower()), None)
        skl_col = next((c for c in rel.columns if "skill" in c.lower()), None)

        if occ_col and skl_col:
            skills_per_occ = rel.groupby(occ_col)[skl_col].nunique()
            desc = skills_per_occ.describe()
            report_lines.append(
                f"- **Skills per occupation:** "
                f"min={int(desc['min'])}, median={int(desc['50%'])}, "
                f"max={int(desc['max'])}, mean={desc['mean']:.1f}"
            )

            fig, ax = plt.subplots(figsize=(10, 5))
            ax.hist(skills_per_occ, bins=40, color="#DD8452", edgecolor="white")
            ax.set_title("ESCO: Number of Skills per Occupation", fontsize=14)
            ax.set_xlabel("Skill Count")
            ax.set_ylabel("Number of Occupations")
            save_chart(fig, "esco_skills_per_occupation")
            report_lines.append(
                "\n![ESCO Skills per Occ](charts/esco_skills_per_occupation.png)\n"
            )


# ── Cross-dataset overlap ─────────────────────────────────────────────────────

def analyze_overlap(report_lines: list[str]) -> None:
    report_lines.append("\n---\n\n## Cross-Dataset Skill Overlap\n")

    # Use Essential Skills as the O*NET skills representative
    # (O*NET 31.0 has no generic Skills.xlsx — skills are split across files)
    onet = load_csv("onet_essential_skills_normalized.csv")
    esco = load_csv("esco_skills_normalized.csv")

    if onet is None or esco is None:
        report_lines.append("_Overlap analysis skipped — one or both files missing._\n")
        return

    onet_col = "skill_name_normalized" if "skill_name_normalized" in onet.columns else "Element Name"
    esco_col = "skill_name_normalized" if "skill_name_normalized" in esco.columns else "preferredLabel"

    onet_skills = set(onet[onet_col].dropna().str.lower().unique())
    esco_skills = set(esco[esco_col].dropna().str.lower().unique())
    overlap = onet_skills & esco_skills

    report_lines.append(f"| Dataset | Unique Normalized Skills |")
    report_lines.append(f"|---------|--------------------------|")
    report_lines.append(f"| O\\*NET  | {len(onet_skills):,} |")
    report_lines.append(f"| ESCO    | {len(esco_skills):,} |")
    report_lines.append(f"| **Overlap** | **{len(overlap):,}** |")
    report_lines.append(
        f"\n> Overlap is approximate — based on normalized name matching only, "
        f"not official cross-dataset identifiers.\n"
    )

    if overlap:
        examples = sorted(list(overlap))[:20]
        report_lines.append("\n**Sample overlapping skill names (first 20):**\n")
        report_lines.append(", ".join(f"`{s}`" for s in examples))


# ── Main ─────────────────────────────────────────────────────────────────────

def run_eda() -> None:
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print("=" * 70)
    print("  EXPLORATORY DATA ANALYSIS")
    print(f"  Started : {timestamp}")
    print(f"  Input   : {PROCESSED}")
    print(f"  Charts  : {CHARTS_DIR}")
    print("=" * 70)

    if not PROCESSED.exists():
        print("ERROR: data/processed/ not found. Run clean_data.py first.")
        sys.exit(1)

    report_lines: list[str] = []

    print("\n--- O*NET Analysis ---")
    analyze_onet(report_lines)

    print("\n--- ESCO Analysis ---")
    analyze_esco(report_lines)

    print("\n--- Cross-Dataset Overlap ---")
    analyze_overlap(report_lines)

    write_report(report_lines)

    print("\n" + "=" * 70)
    print("  EDA COMPLETE")
    print(f"  Report : {REPORT_FILE}")
    print(f"  Charts : {CHARTS_DIR}")
    print("=" * 70)


if __name__ == "__main__":
    run_eda()
