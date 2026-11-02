"""
matching_engine.py
------------------
Core career matching and skill gap analysis engine.

PURPOSE:
    Given a student skill profile (dict of skill_name → rating 1–5),
    computes a CareerMatchScore and prioritised SkillGapList for every
    occupation in the career_skill_map.

ALGORITHM (approved 2026-10-25):
    1. Map student rating (1–5) → O*NET Level scale (0–7) linearly.
    2. For each (occupation, skill):
         skill_score = min(student_level, required_level) / required_level
         weighted    = skill_score × importance_score
    3. CareerMatchScore = sum(weighted) / sum(importance_score) × 100
    4. SkillGap priority = (required_level - student_level) × importance_score

DEFAULTS (all labelled illustrative — change in MATCHING_CONFIG):
    - Minimum importance threshold: 1.5 (inherited from career_skill_map)
    - Minimum match score to include in results: 20%
    - Top-N careers returned: 10
    - RIASEC interest bonus: +3% per matching top-3 interest (optional)

INPUTS:
    data/processed/career_skill_map.csv
    data/processed/career_metadata.csv

USAGE:
    from data_processing.matching_engine import CareerMatcher
    matcher = CareerMatcher()
    results = matcher.match(student_profile)

    Or run directly for a demo:
    python data_processing/matching_engine.py
"""

import sys
import pandas as pd
import numpy as np
from pathlib import Path
from dataclasses import dataclass, field

# ── Paths ────────────────────────────────────────────────────────────────────
PROJECT_ROOT = Path(__file__).resolve().parent.parent
PROCESSED    = PROJECT_ROOT / "data" / "processed"

# ── Configuration ─────────────────────────────────────────────────────────────
MATCHING_CONFIG = {
    # Student scale (1–5) → O*NET Level scale (0–7): linear mapping
    # formula: onet_level = (student_rating - 1) × (7 / 4)
    "student_min": 1,
    "student_max": 5,
    "onet_level_min": 0.0,
    "onet_level_max": 7.0,

    # Return careers scoring at or above this threshold (0–100)
    # Illustrative default: 20%
    "min_match_score": 20.0,

    # Number of top career results to return
    "top_n": 10,

    # RIASEC interest bonus per matching interest type (0–5 scale percent)
    # Set to 0.0 to disable interest bonus entirely
    "riasec_bonus_per_match": 3.0,
    "riasec_max_bonus": 5.0,   # cap total bonus

    # Gap priority thresholds for colour coding
    "gap_critical_threshold": 10.0,   # red  — fix immediately
    "gap_moderate_threshold": 5.0,    # amber — improve soon
}

# ── Data classes ─────────────────────────────────────────────────────────────

@dataclass
class SkillGap:
    """One skill gap entry for a career recommendation."""
    element_name:     str
    skill_category:   str
    student_level:    float     # mapped to O*NET 0–7 scale
    required_level:   float     # O*NET Level score
    importance:       float     # O*NET Importance score (1–5)
    gap_size:         float     # required - student (0 if student exceeds)
    gap_priority:     float     # gap_size × importance
    priority_label:   str       # "Critical" / "Moderate" / "Minor"
    student_rating:   int       # original 1–5 student input


@dataclass
class CareerResult:
    """One career recommendation with match score and skill gaps."""
    rank:             int
    onet_soc_code:    str
    occupation_title: str
    description:      str
    job_zone:         int | None
    riasec_codes:     list[str]
    match_score:      float          # 0–100 (not a probability)
    base_score:       float          # score before RIASEC bonus
    riasec_bonus:     float          # bonus added from interest match
    strong_skills:    list[str]      # skills where student meets/exceeds requirement
    skill_gaps:       list[SkillGap] # sorted by gap_priority descending
    skills_assessed:  int            # number of career skills student rated
    skills_total:     int            # total skills in career map


# ── Core engine ───────────────────────────────────────────────────────────────

class CareerMatcher:
    """
    Loads the career-skill map once and matches student profiles on demand.

    Usage:
        matcher = CareerMatcher()
        results = matcher.match(
            student_skills={"Java": 4, "SQL": 4, "Spring Boot": 1, "Git": 3},
            student_interests=["I", "C"],   # optional RIASEC codes
            top_n=10,
        )
    """

    def __init__(self) -> None:
        self._map:  pd.DataFrame | None = None
        self._meta: pd.DataFrame | None = None
        self._load_data()

    def _load_data(self) -> None:
        map_path  = PROCESSED / "career_skill_map.csv"
        meta_path = PROCESSED / "career_metadata.csv"

        if not map_path.exists():
            raise FileNotFoundError(
                f"career_skill_map.csv not found. "
                f"Run build_career_skill_map.py first.\n  Expected: {map_path}"
            )
        if not meta_path.exists():
            raise FileNotFoundError(
                f"career_metadata.csv not found. "
                f"Run build_career_skill_map.py first.\n  Expected: {meta_path}"
            )

        self._map  = pd.read_csv(map_path,  encoding="utf-8", low_memory=False)
        self._meta = pd.read_csv(meta_path, encoding="utf-8", low_memory=False)
        print(f"CareerMatcher loaded: {self._map['onet_soc_code'].nunique():,} occupations, "
              f"{len(self._map):,} skill-occupation pairs")

    # ── Scale conversion ──────────────────────────────────────────────────────

    @staticmethod
    def student_to_onet_level(student_rating: int | float) -> float:
        """
        Map student 1–5 rating → O*NET Level 0–7 scale (linear).
        Formula: onet_level = (rating - 1) × (7 / 4)
        Clamps input to [1, 5].
        """
        cfg = MATCHING_CONFIG
        rating = max(cfg["student_min"], min(cfg["student_max"], float(student_rating)))
        onet_level = (rating - 1) * (cfg["onet_level_max"] / (cfg["student_max"] - cfg["student_min"]))
        return round(onet_level, 4)

    # ── Gap priority labelling ────────────────────────────────────────────────

    @staticmethod
    def _priority_label(gap_priority: float) -> str:
        cfg = MATCHING_CONFIG
        if gap_priority >= cfg["gap_critical_threshold"]:
            return "Critical"
        elif gap_priority >= cfg["gap_moderate_threshold"]:
            return "Moderate"
        else:
            return "Minor"

    # ── RIASEC bonus ──────────────────────────────────────────────────────────

    @staticmethod
    def _compute_riasec_bonus(
        student_interests: list[str],
        career_riasec: list[str],
    ) -> float:
        """
        Add a small bonus for each student interest that matches the
        career's top RIASEC codes. Capped at riasec_max_bonus.
        """
        if not student_interests or not career_riasec:
            return 0.0
        cfg = MATCHING_CONFIG
        student_set = set(i.upper() for i in student_interests)
        career_set  = set(c.upper() for c in career_riasec if isinstance(c, str) and c != "?")
        matches = len(student_set & career_set)
        bonus = matches * cfg["riasec_bonus_per_match"]
        return min(bonus, cfg["riasec_max_bonus"])

    # ── Score computation for one occupation ──────────────────────────────────

    def _score_occupation(
        self,
        occ_df: pd.DataFrame,
        student_levels: dict[str, float],   # element_name (lower) → onet_level
        student_ratings: dict[str, int],    # element_name (lower) → raw 1–5
        assessed_only: bool = False,
    ) -> tuple[float, list[str], list[SkillGap], int, int]:
        """
        Compute match score and gaps for one occupation.
        Returns: (base_score, strong_skills, gaps, skills_assessed, skills_total)
        """
        total_importance = 0.0
        weighted_score   = 0.0
        strong_skills    = []
        gaps             = []
        skills_assessed  = 0
        skills_total     = len(occ_df)

        for _, row in occ_df.iterrows():
            elem_key      = str(row["element_name"]).lower().strip()
            if assessed_only and elem_key not in student_levels:
                continue   # Quick-profile mode scores only skills the student rated.
            importance    = float(row["importance_score"]) if pd.notna(row["importance_score"]) else 0.0
            required_lv   = float(row["level_score"])      if pd.notna(row["level_score"])      else 0.0

            if importance <= 0 or required_lv <= 0:
                continue   # skip unscored rows

            student_lv = student_levels.get(elem_key, 0.0)
            if elem_key in student_levels:
                skills_assessed += 1

            # Skill score: capped at 1.0 if student meets or exceeds requirement
            skill_score = min(student_lv, required_lv) / required_lv

            total_importance += importance
            weighted_score   += skill_score * importance

            gap_size = max(0.0, required_lv - student_lv)

            if gap_size <= 0.0:
                strong_skills.append(str(row["element_name"]))
            else:
                gap_priority = gap_size * importance
                gaps.append(SkillGap(
                    element_name   = str(row["element_name"]),
                    skill_category = str(row["skill_category"]),
                    student_level  = student_lv,
                    required_level = required_lv,
                    importance     = importance,
                    gap_size       = round(gap_size, 3),
                    gap_priority   = round(gap_priority, 3),
                    priority_label = self._priority_label(gap_priority),
                    student_rating = student_ratings.get(elem_key, 0),
                ))

        if assessed_only:
            skills_total = skills_assessed

        if total_importance == 0:
            return 0.0, [], [], 0, skills_total

        base_score = (weighted_score / total_importance) * 100
        gaps_sorted = sorted(gaps, key=lambda g: g.gap_priority, reverse=True)
        return round(base_score, 2), strong_skills, gaps_sorted, skills_assessed, skills_total

    # ── Public API ────────────────────────────────────────────────────────────

    def match(
        self,
        student_skills: dict[str, int],
        student_interests: list[str] | None = None,
        top_n: int | None = None,
        min_score: float | None = None,
        assessed_only: bool = False,
    ) -> list[CareerResult]:
        """
        Match a student profile against all careers.

        Args:
            student_skills:    {skill_name: rating_1_to_5}
                               Skill names should match O*NET Element Names.
                               Example: {"Reading Comprehension": 4, "Mathematics": 3}
            student_interests: Optional list of RIASEC codes e.g. ["I", "C"]
            top_n:             Number of results to return (default from config)
            min_score:         Minimum score threshold (default from config)
            assessed_only:     If true, score only skills the student explicitly rated.

        Returns:
            List of CareerResult objects sorted by match_score descending.

        Note:
            Match scores are NOT probabilities of employment or career success.
            They reflect weighted skill coverage only.
        """
        cfg       = MATCHING_CONFIG
        top_n     = top_n     or cfg["top_n"]
        min_score = min_score if min_score is not None else cfg["min_match_score"]

        # Normalise student skill keys to lowercase for matching
        student_levels  = {k.lower().strip(): self.student_to_onet_level(v)
                           for k, v in student_skills.items()}
        student_ratings = {k.lower().strip(): int(v)
                           for k, v in student_skills.items()}

        results = []
        grouped = self._map.groupby("onet_soc_code")

        for soc_code, occ_df in grouped:
            base_score, strong, gaps, assessed, total = self._score_occupation(
                occ_df, student_levels, student_ratings, assessed_only=assessed_only
            )

            # RIASEC interest bonus
            career_riasec = []
            meta_row = self._meta[self._meta["onet_soc_code"] == soc_code]
            occ_title = occ_df["occupation_title"].iloc[0] if len(occ_df) > 0 else soc_code
            occ_desc  = ""
            job_zone  = None

            if not meta_row.empty:
                row = meta_row.iloc[0]
                occ_title    = str(row.get("occupation_title", occ_title))
                occ_desc     = str(row.get("description", ""))
                job_zone_val = row.get("job_zone")
                job_zone     = int(job_zone_val) if pd.notna(job_zone_val) else None
                for col in ["riasec_top1", "riasec_top2", "riasec_top3"]:
                    val = row.get(col)
                    if pd.notna(val) and str(val) not in ("?", "nan"):
                        career_riasec.append(str(val))

            riasec_bonus = self._compute_riasec_bonus(
                student_interests or [], career_riasec
            )
            final_score = round(min(100.0, base_score + riasec_bonus), 2)

            if final_score < min_score:
                continue

            results.append(CareerResult(
                rank             = 0,     # assigned after sorting
                onet_soc_code    = str(soc_code),
                occupation_title = occ_title,
                description      = occ_desc[:200] if occ_desc else "",
                job_zone         = job_zone,
                riasec_codes     = career_riasec,
                match_score      = final_score,
                base_score       = base_score,
                riasec_bonus     = riasec_bonus,
                strong_skills    = strong,
                skill_gaps       = gaps,
                skills_assessed  = assessed,
                skills_total     = total,
            ))

        # Sort and rank
        results.sort(key=lambda r: r.match_score, reverse=True)
        for i, r in enumerate(results[:top_n], start=1):
            r.rank = i

        return results[:top_n]

    def format_results(self, results: list[CareerResult]) -> str:
        """Format results as a human-readable string for console display."""
        if not results:
            return "No careers matched above the minimum score threshold."

        lines = []
        lines.append("=" * 70)
        lines.append("  CAREER MATCH RESULTS")
        lines.append("  Note: Scores reflect skill coverage only — not employment probability")
        lines.append("=" * 70)

        for r in results:
            lines.append(f"\n#{r.rank}  {r.occupation_title}")
            lines.append(f"     SOC Code   : {r.onet_soc_code}")
            lines.append(f"     Match Score: {r.match_score:.1f}%"
                         + (f" (base {r.base_score:.1f}% + {r.riasec_bonus:.1f}% interest bonus)"
                            if r.riasec_bonus > 0 else ""))
            if r.job_zone:
                zone_labels = {2:"Some prep", 3:"Medium prep",
                               4:"Considerable prep", 5:"Extensive prep"}
                lines.append(f"     Job Zone   : {r.job_zone} — {zone_labels.get(r.job_zone,'')}")
            if r.riasec_codes:
                lines.append(f"     RIASEC     : {''.join(r.riasec_codes)}")
            lines.append(f"     Skills     : {r.skills_assessed}/{r.skills_total} rated by student")

            if r.strong_skills:
                lines.append(f"     ✅ Strong  : {', '.join(r.strong_skills[:5])}"
                             + ("..." if len(r.strong_skills) > 5 else ""))

            critical = [g for g in r.skill_gaps if g.priority_label == "Critical"]
            moderate = [g for g in r.skill_gaps if g.priority_label == "Moderate"]

            if critical:
                lines.append(f"     🔴 Critical gaps ({len(critical)}):")
                for g in critical[:3]:
                    lines.append(f"         {g.element_name} "
                                 f"(have: {g.student_rating}/5, need ~{round(g.required_level/7*5)}/5, "
                                 f"importance: {g.importance:.1f})")
            if moderate:
                lines.append(f"     🟡 Moderate gaps ({len(moderate)}):")
                for g in moderate[:3]:
                    lines.append(f"         {g.element_name} "
                                 f"(have: {g.student_rating}/5, need ~{round(g.required_level/7*5)}/5)")

        lines.append("\n" + "=" * 70)
        return "\n".join(lines)
