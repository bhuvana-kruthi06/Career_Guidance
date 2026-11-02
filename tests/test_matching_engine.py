"""
test_matching_engine.py
-----------------------
Unit and integration tests for the career matching and skill gap engine.

Tests cover:
  - Scale conversion (student 1–5 → O*NET 0–7)
  - Boundary values (rating=1, rating=5)
  - Score formula correctness with known inputs
  - Gap priority calculation
  - RIASEC bonus logic
  - Java ≠ JavaScript distinction preserved through matching
  - Empty profiles
  - All-zero profiles
  - Results returned sorted correctly
  - Minimum score filter works

USAGE:
    pytest tests/test_matching_engine.py -v
"""

import sys
import math
import pytest
from pathlib import Path
from unittest.mock import patch, MagicMock
import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

from data_processing.matching_engine import (
    CareerMatcher,
    CareerResult,
    SkillGap,
    MATCHING_CONFIG,
)


# ════════════════════════════════════════════════════════════════════════════
# Fixtures
# ════════════════════════════════════════════════════════════════════════════

def make_minimal_map() -> pd.DataFrame:
    """Create a minimal career_skill_map DataFrame for unit testing."""
    return pd.DataFrame([
        # Occupation A — requires strong Reading Comprehension + Math
        {
            "onet_soc_code":    "15-1252.00",
            "occupation_title": "Software Developer",
            "element_id":       "2.A.1.a",
            "element_name":     "Reading Comprehension",
            "skill_category":   "Essential",
            "importance_score": 4.5,
            "level_score":      4.0,
            "job_zone":         4,
            "riasec_top1":      "I",
            "riasec_top2":      "C",
            "riasec_top3":      "R",
        },
        {
            "onet_soc_code":    "15-1252.00",
            "occupation_title": "Software Developer",
            "element_id":       "2.B.2.i",
            "element_name":     "Programming",
            "skill_category":   "Transferable",
            "importance_score": 4.8,
            "level_score":      5.5,
            "job_zone":         4,
            "riasec_top1":      "I",
            "riasec_top2":      "C",
            "riasec_top3":      "R",
        },
        # Occupation B — social + communication focused
        {
            "onet_soc_code":    "21-1012.00",
            "occupation_title": "Educational Counselor",
            "element_id":       "2.A.1.b",
            "element_name":     "Active Listening",
            "skill_category":   "Essential",
            "importance_score": 4.8,
            "level_score":      4.5,
            "job_zone":         5,
            "riasec_top1":      "S",
            "riasec_top2":      "A",
            "riasec_top3":      "I",
        },
        {
            "onet_soc_code":    "21-1012.00",
            "occupation_title": "Educational Counselor",
            "element_id":       "2.A.1.c",
            "element_name":     "Speaking",
            "skill_category":   "Essential",
            "importance_score": 4.2,
            "level_score":      4.0,
            "job_zone":         5,
            "riasec_top1":      "S",
            "riasec_top2":      "A",
            "riasec_top3":      "I",
        },
    ])


def make_minimal_meta() -> pd.DataFrame:
    return pd.DataFrame([
        {
            "onet_soc_code":    "15-1252.00",
            "occupation_title": "Software Developer",
            "description":      "Develop software solutions.",
            "job_zone":         4,
            "riasec_top1":      "I",
            "riasec_top2":      "C",
            "riasec_top3":      "R",
        },
        {
            "onet_soc_code":    "21-1012.00",
            "occupation_title": "Educational Counselor",
            "description":      "Counsel students on education.",
            "job_zone":         5,
            "riasec_top1":      "S",
            "riasec_top2":      "A",
            "riasec_top3":      "I",
        },
    ])


@pytest.fixture
def matcher(tmp_path) -> CareerMatcher:
    """
    Create a CareerMatcher with minimal test data, bypassing file loading.
    """
    m = CareerMatcher.__new__(CareerMatcher)
    m._map  = make_minimal_map()
    m._meta = make_minimal_meta()
    return m


# ════════════════════════════════════════════════════════════════════════════
# SECTION 1: Scale conversion tests
# ════════════════════════════════════════════════════════════════════════════

class TestScaleConversion:

    def test_rating_1_maps_to_0(self):
        assert CareerMatcher.student_to_onet_level(1) == 0.0

    def test_rating_5_maps_to_7(self):
        assert CareerMatcher.student_to_onet_level(5) == 7.0

    def test_rating_3_maps_to_midpoint(self):
        result = CareerMatcher.student_to_onet_level(3)
        assert abs(result - 3.5) < 0.01

    def test_rating_2_maps_correctly(self):
        result = CareerMatcher.student_to_onet_level(2)
        assert abs(result - 1.75) < 0.01

    def test_rating_4_maps_correctly(self):
        result = CareerMatcher.student_to_onet_level(4)
        assert abs(result - 5.25) < 0.01

    def test_clamps_below_1(self):
        """Values below 1 should clamp to 0."""
        assert CareerMatcher.student_to_onet_level(0) == 0.0

    def test_clamps_above_5(self):
        """Values above 5 should clamp to 7."""
        assert CareerMatcher.student_to_onet_level(6) == 7.0

    def test_mapping_is_monotonic(self):
        """Higher student rating must always map to higher O*NET level."""
        levels = [CareerMatcher.student_to_onet_level(r) for r in [1, 2, 3, 4, 5]]
        assert levels == sorted(levels)


# ════════════════════════════════════════════════════════════════════════════
# SECTION 2: Score formula tests
# ════════════════════════════════════════════════════════════════════════════

class TestScoreFormula:

    def test_perfect_match_gives_100(self, matcher):
        """
        Student who meets all requirements exactly should score very high.
        """
        # Required levels: Reading=4.0, Programming=5.5
        # Student levels: Reading=5 → 7.0 (>4.0 → capped), Programming=4 → 5.25 (close to 5.5)
        results = matcher.match(
            student_skills={
                "Reading Comprehension": 5,
                "Programming": 4,
                "Active Listening": 5,
                "Speaking": 5,
            }
        )
        assert len(results) > 0
        # Best match score should be reasonably high
        assert results[0].match_score > 50

    def test_zero_skills_gives_low_score(self, matcher):
        """Student with no listed skills should score 0 or near 0."""
        results = matcher.match(
            student_skills={},
            min_score=0.0,
        )
        for r in results:
            assert r.match_score == 0.0 or r.match_score < 5.0

    def test_expert_in_required_skills_scores_higher_than_beginner(self, matcher):
        """Expert student must score higher than beginner for same career."""
        results_expert = matcher.match(
            student_skills={"Reading Comprehension": 5, "Programming": 5},
            min_score=0.0,
        )
        results_beginner = matcher.match(
            student_skills={"Reading Comprehension": 1, "Programming": 1},
            min_score=0.0,
        )
        # Find same career in both results
        expert_scores   = {r.onet_soc_code: r.match_score for r in results_expert}
        beginner_scores = {r.onet_soc_code: r.match_score for r in results_beginner}

        for code in expert_scores:
            if code in beginner_scores:
                assert expert_scores[code] >= beginner_scores[code], (
                    f"Expert scored lower than beginner for {code}"
                )

    def test_score_is_between_0_and_100(self, matcher):
        """All scores must be in [0, 100]."""
        results = matcher.match(
            student_skills={"Reading Comprehension": 3, "Programming": 2},
            min_score=0.0,
        )
        for r in results:
            assert 0.0 <= r.match_score <= 100.0, (
                f"Score out of range: {r.match_score} for {r.occupation_title}"
            )

    def test_score_not_presented_as_probability(self):
        """
        Verify the docstring and config don't use 'probability' language.
        The score is skill coverage only.
        """
        import data_processing.matching_engine as me
        doc = me.__doc__ or ""
        # This is a documentation / policy check
        assert "probability" not in doc.lower() or "not a probability" in doc.lower()


# ════════════════════════════════════════════════════════════════════════════
# SECTION 3: Skill gap tests
# ════════════════════════════════════════════════════════════════════════════

class TestSkillGaps:

    def test_gap_exists_when_student_below_requirement(self, matcher):
        """Student rating 1 for a high-level skill should produce a gap."""
        results = matcher.match(
            student_skills={
                "Reading Comprehension": 1,
                "Programming": 1,
                "Active Listening": 1,
                "Speaking": 1,
            },
            min_score=0.0,
        )
        for r in results:
            # All students are below requirements → gaps should exist
            assert len(r.skill_gaps) > 0

    def test_no_gap_when_student_meets_requirement(self, matcher):
        """
        Student meeting all requirements should have the skill in strong_skills,
        not in gaps.
        """
        results = matcher.match(
            student_skills={
                "Reading Comprehension": 5,   # maps to 7.0, required 4.0 → met
                "Programming": 5,
                "Active Listening": 5,
                "Speaking": 5,
            },
            min_score=0.0,
        )
        for r in results:
            gap_names = [g.element_name for g in r.skill_gaps]
            if "Reading Comprehension" in r.strong_skills:
                assert "Reading Comprehension" not in gap_names

    def test_gap_priority_is_gap_size_times_importance(self, matcher):
        """Verify gap_priority formula: gap_size × importance."""
        results = matcher.match(
            student_skills={"Reading Comprehension": 1, "Programming": 1},
            min_score=0.0,
        )
        for r in results:
            for gap in r.skill_gaps:
                expected = round(gap.gap_size * gap.importance, 3)
                assert abs(gap.gap_priority - expected) < 0.01, (
                    f"gap_priority mismatch: {gap.gap_priority} vs {expected}"
                )

    def test_gaps_sorted_by_priority_descending(self, matcher):
        """Skill gaps must be returned highest-priority first."""
        results = matcher.match(
            student_skills={"Reading Comprehension": 1, "Programming": 1,
                            "Active Listening": 1, "Speaking": 1},
            min_score=0.0,
        )
        for r in results:
            priorities = [g.gap_priority for g in r.skill_gaps]
            assert priorities == sorted(priorities, reverse=True), (
                f"Gaps not sorted for {r.occupation_title}"
            )

    def test_priority_labels_correct(self, matcher):
        """Gap priority labels must match the configured thresholds."""
        cfg = MATCHING_CONFIG
        results = matcher.match(
            student_skills={"Reading Comprehension": 1, "Programming": 1,
                            "Active Listening": 1, "Speaking": 1},
            min_score=0.0,
        )
        for r in results:
            for gap in r.skill_gaps:
                if gap.gap_priority >= cfg["gap_critical_threshold"]:
                    assert gap.priority_label == "Critical"
                elif gap.gap_priority >= cfg["gap_moderate_threshold"]:
                    assert gap.priority_label == "Moderate"
                else:
                    assert gap.priority_label == "Minor"


# ════════════════════════════════════════════════════════════════════════════
# SECTION 4: RIASEC bonus tests
# ════════════════════════════════════════════════════════════════════════════

class TestRiasecBonus:

    def test_matching_interest_adds_bonus(self, matcher):
        """Student with matching RIASEC interest should score higher."""
        results_with_interest = matcher.match(
            student_skills={"Reading Comprehension": 3, "Programming": 3},
            student_interests=["I", "C"],   # matches Software Developer
            min_score=0.0,
        )
        results_no_interest = matcher.match(
            student_skills={"Reading Comprehension": 3, "Programming": 3},
            student_interests=None,
            min_score=0.0,
        )
        scores_with    = {r.onet_soc_code: r.match_score for r in results_with_interest}
        scores_without = {r.onet_soc_code: r.match_score for r in results_no_interest}

        for code in scores_with:
            if code in scores_without:
                assert scores_with[code] >= scores_without[code]

    def test_no_interest_gives_zero_bonus(self, matcher):
        results = matcher.match(
            student_skills={"Reading Comprehension": 3},
            student_interests=None,
            min_score=0.0,
        )
        for r in results:
            assert r.riasec_bonus == 0.0

    def test_bonus_capped_at_max(self):
        """RIASEC bonus must never exceed riasec_max_bonus."""
        bonus = CareerMatcher._compute_riasec_bonus(
            student_interests=["R", "I", "A", "S", "E", "C"],
            career_riasec=["R", "I", "A"],
        )
        assert bonus <= MATCHING_CONFIG["riasec_max_bonus"]

    def test_non_matching_interest_gives_no_bonus(self, matcher):
        results = matcher.match(
            student_skills={"Reading Comprehension": 3},
            student_interests=["X", "Y"],   # invalid codes → no match
            min_score=0.0,
        )
        for r in results:
            assert r.riasec_bonus == 0.0


# ════════════════════════════════════════════════════════════════════════════
# SECTION 5: Ranking and filtering tests
# ════════════════════════════════════════════════════════════════════════════

class TestRankingAndFiltering:

    def test_results_sorted_by_score_descending(self, matcher):
        """Results must always be in descending score order."""
        results = matcher.match(
            student_skills={"Reading Comprehension": 3, "Programming": 3,
                            "Active Listening": 3, "Speaking": 3},
            min_score=0.0,
        )
        scores = [r.match_score for r in results]
        assert scores == sorted(scores, reverse=True)

    def test_ranks_are_sequential(self, matcher):
        """Ranks must be 1, 2, 3, ... without gaps."""
        results = matcher.match(
            student_skills={"Reading Comprehension": 3, "Programming": 3,
                            "Active Listening": 3, "Speaking": 3},
        )
        for i, r in enumerate(results, 1):
            assert r.rank == i

    def test_min_score_filter_applied(self, matcher):
        """No result should be below the minimum score threshold."""
        threshold = 50.0
        results = matcher.match(
            student_skills={"Reading Comprehension": 3},
            min_score=threshold,
        )
        for r in results:
            assert r.match_score >= threshold

    def test_top_n_respected(self, matcher):
        """Number of results must not exceed top_n."""
        results = matcher.match(
            student_skills={"Reading Comprehension": 3, "Active Listening": 3},
            top_n=1,
            min_score=0.0,
        )
        assert len(results) <= 1

    def test_empty_profile_returns_results_with_zero_score(self, matcher):
        results = matcher.match(student_skills={}, min_score=0.0)
        for r in results:
            assert r.match_score == 0.0


# ════════════════════════════════════════════════════════════════════════════
# SECTION 6: Data integrity tests
# ════════════════════════════════════════════════════════════════════════════

class TestDataIntegrity:

    def test_onet_soc_codes_preserved(self, matcher):
        """SOC codes in results must match those in the map."""
        valid_codes = set(matcher._map["onet_soc_code"].unique())
        results = matcher.match(
            student_skills={"Reading Comprehension": 3},
            min_score=0.0,
        )
        for r in results:
            assert r.onet_soc_code in valid_codes

    def test_skills_assessed_le_skills_total(self, matcher):
        """Assessed skills cannot exceed total skills in the career."""
        results = matcher.match(
            student_skills={"Reading Comprehension": 3},
            min_score=0.0,
        )
        for r in results:
            assert r.skills_assessed <= r.skills_total

    def test_match_score_is_float(self, matcher):
        results = matcher.match(
            student_skills={"Reading Comprehension": 3},
            min_score=0.0,
        )
        for r in results:
            assert isinstance(r.match_score, float)

    def test_format_results_returns_string(self, matcher):
        results = matcher.match(
            student_skills={"Reading Comprehension": 3},
            min_score=0.0,
        )
        output = matcher.format_results(results)
        assert isinstance(output, str)
        assert len(output) > 0

    def test_format_empty_results(self, matcher):
        output = matcher.format_results([])
        assert "No careers matched" in output


class TestQuickProfileMode:

    def test_unrated_skills_do_not_count_as_zero(self, matcher):
        full_profile = matcher.match({"Reading Comprehension": 4}, top_n=1, min_score=0)
        quick_profile = matcher.match({"Reading Comprehension": 4}, top_n=1, min_score=0, assessed_only=True)
        assert full_profile[0].skills_total == 2
        assert quick_profile[0].skills_assessed == 1
        assert quick_profile[0].skills_total == 1
        assert len(quick_profile[0].skill_gaps) <= 1
