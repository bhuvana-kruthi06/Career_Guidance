"""
test_pipeline.py
----------------
Automated tests for the AI Career Guidance data processing pipeline.

Tests cover:
  - Missing value handling
  - Duplicate removal
  - Whitespace stripping
  - Alias map correctness (JS->JavaScript, Java NOT->JavaScript)
  - ID preservation
  - Invalid / empty file handling
  - Normalization flag values
  - Output paths exist after cleaning

USAGE:
    Activate venv:  venv\\Scripts\\activate
    Run all tests:  pytest tests/ -v
    Run with coverage: pytest tests/ -v --cov=data_processing
"""

import sys
import pytest
import pandas as pd
import numpy as np
from pathlib import Path
from io import StringIO

# Add project root to path so we can import our modules
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "data_processing"))

from clean_data import (
    clean_whitespace,
    drop_empty_rows,
    drop_duplicates,
    preserve_name_original,
    clean_dataframe,
)
from normalize_skills import (
    normalize_skill_name,
    normalize_dataframe,
    ALIAS_MAP,
    UNCERTAIN_ALIASES,
)


# ════════════════════════════════════════════════════════════════════════════
# SECTION 1: clean_data.py tests
# ════════════════════════════════════════════════════════════════════════════

class TestWhitespaceCleaning:
    """Whitespace stripping and line ending normalisation."""

    def test_strips_leading_trailing_spaces(self):
        df = pd.DataFrame({"name": ["  Python  ", " Java ", "SQL"]})
        result = clean_whitespace(df)
        assert result["name"].tolist() == ["Python", "Java", "SQL"]

    def test_strips_tabs(self):
        df = pd.DataFrame({"name": ["\tPython\t", "Java"]})
        result = clean_whitespace(df)
        assert result["name"][0] == "Python"

    def test_flattens_newlines_within_cell(self):
        df = pd.DataFrame({"desc": ["Line1\nLine2", "A\r\nB"]})
        result = clean_whitespace(df)
        assert "\n" not in result["desc"][0]
        assert "\r" not in result["desc"][1]

    def test_numeric_columns_unchanged(self):
        df = pd.DataFrame({"value": [1.5, 2.0, 3.7]})
        result = clean_whitespace(df)
        assert result["value"].tolist() == [1.5, 2.0, 3.7]

    def test_empty_string_becomes_na(self):
        df = pd.DataFrame({"name": ["  ", "Valid"]})
        result = clean_whitespace(df)
        # Stripped spaces become empty string; preserve_name_original handles NA
        assert result["name"][0].strip() == "" or pd.isna(result["name"][0])


class TestEmptyRowRemoval:
    """Fully empty rows are dropped; partial rows are kept."""

    def test_fully_empty_row_removed(self):
        df = pd.DataFrame({
            "a": [1, None, 3],
            "b": ["x", None, "z"]
        })
        result = drop_empty_rows(df)
        assert len(result) == 2

    def test_partial_row_kept(self):
        df = pd.DataFrame({
            "a": [1, None],
            "b": ["x", "y"]
        })
        result = drop_empty_rows(df)
        assert len(result) == 2   # second row has "y" in column b — not empty

    def test_all_empty_rows_removed(self):
        df = pd.DataFrame({"a": [None, None], "b": [None, None]})
        result = drop_empty_rows(df)
        assert len(result) == 0

    def test_no_rows_removed_when_none_empty(self):
        df = pd.DataFrame({"a": [1, 2], "b": ["x", "y"]})
        result = drop_empty_rows(df)
        assert len(result) == 2


class TestDuplicateRemoval:
    """Exact duplicates are removed; non-duplicates are kept."""

    def test_exact_duplicate_removed(self):
        df = pd.DataFrame({"a": [1, 1, 2], "b": ["x", "x", "y"]})
        result = drop_duplicates(df)
        assert len(result) == 2

    def test_near_duplicate_kept(self):
        # Same value different case — NOT a duplicate
        df = pd.DataFrame({"a": ["Python", "python"]})
        result = drop_duplicates(df)
        assert len(result) == 2

    def test_no_duplicates_unchanged(self):
        df = pd.DataFrame({"a": [1, 2, 3]})
        result = drop_duplicates(df)
        assert len(result) == 3

    def test_all_duplicates(self):
        df = pd.DataFrame({"a": [1, 1, 1]})
        result = drop_duplicates(df)
        assert len(result) == 1


class TestPreserveOriginal:
    """Original column is preserved before any transformation."""

    def test_original_column_added(self):
        df = pd.DataFrame({"Element Name": ["Python", "SQL"]})
        result = preserve_name_original(df, "Element Name")
        assert "Element Name_original" in result.columns

    def test_original_values_match_source(self):
        df = pd.DataFrame({"Element Name": ["Python", "SQL"]})
        result = preserve_name_original(df, "Element Name")
        assert result["Element Name_original"].tolist() == ["Python", "SQL"]

    def test_idempotent_does_not_duplicate_column(self):
        df = pd.DataFrame({"Element Name": ["Python"], "Element Name_original": ["Python"]})
        result = preserve_name_original(df, "Element Name")
        # Should not add a second _original column
        assert list(result.columns).count("Element Name_original") == 1

    def test_missing_column_no_error(self):
        df = pd.DataFrame({"Other Column": ["A", "B"]})
        result = preserve_name_original(df, "Element Name")
        assert "Element Name_original" not in result.columns


class TestCleanDataframe:
    """Integration test for the full clean_dataframe function."""

    def test_row_counts_reported(self):
        df = pd.DataFrame({"a": [1, 1, None], "b": ["x", "x", None]})
        _, log = clean_dataframe(df, "test_file")
        assert log["rows_before"] == 3
        assert log["rows_after"] < 3

    def test_empty_file_returns_empty_dataframe(self):
        df = pd.DataFrame({"a": pd.Series([], dtype="object")})
        result, log = clean_dataframe(df, "empty_file")
        assert len(result) == 0
        assert log["rows_after"] == 0


# ════════════════════════════════════════════════════════════════════════════
# SECTION 2: normalize_skills.py tests
# ════════════════════════════════════════════════════════════════════════════

class TestAliasMap:
    """Critical alias mappings must be correct."""

    def test_js_maps_to_javascript(self):
        name, flag = normalize_skill_name("JS")
        assert name == "JavaScript"
        assert flag == "OK"

    def test_javascript_lowercase_maps_correctly(self):
        name, flag = normalize_skill_name("javascript")
        assert name == "JavaScript"
        assert flag == "OK"

    def test_java_does_NOT_map_to_javascript(self):
        """CRITICAL: Java must never be merged with JavaScript."""
        name, flag = normalize_skill_name("Java")
        assert name != "JavaScript", (
            "CRITICAL ERROR: 'Java' was mapped to 'JavaScript'. "
            "These are distinct technologies and must never be merged."
        )

    def test_java_programming_maps_to_java(self):
        name, flag = normalize_skill_name("Java programming")
        assert name == "Java"
        assert flag == "OK"

    def test_sql_variants(self):
        name1, _ = normalize_skill_name("Structured Query Language")
        name2, _ = normalize_skill_name("sql")
        assert name1 == "SQL"
        assert name2 == "SQL"

    def test_case_insensitive_matching(self):
        name, flag = normalize_skill_name("JAVASCRIPT")
        assert name == "JavaScript"

    def test_unknown_skill_returns_original(self):
        name, flag = normalize_skill_name("Some Very Obscure Skill XYZ")
        assert name == "Some Very Obscure Skill XYZ"
        assert flag == "NO_MATCH"

    def test_empty_string_returns_no_match(self):
        name, flag = normalize_skill_name("")
        assert flag == "NO_MATCH"

    def test_nan_input_handled(self):
        name, flag = normalize_skill_name(float("nan"))
        assert flag == "NO_MATCH"

    def test_uncertain_alias_flagged_for_review(self):
        # 'c#' is in UNCERTAIN_ALIASES
        name, flag = normalize_skill_name("c#")
        assert flag == "REVIEW_NEEDED"

    def test_alias_map_has_no_java_to_javascript(self):
        """Verify at the map level that Java is never mapped to JavaScript."""
        for raw, canonical in ALIAS_MAP.items():
            if "java" in raw and "script" not in raw:
                assert canonical != "JavaScript", (
                    f"ALIAS_MAP maps '{raw}' -> 'JavaScript' incorrectly. "
                    "Java and JavaScript must remain distinct."
                )


class TestNormalizeDataframe:
    """normalize_dataframe function tests."""

    def test_adds_normalization_columns(self):
        df = pd.DataFrame({
            "Element Name": ["JavaScript", "Java", "SQL"],
            "Element ID": ["KS001", "KS002", "KS003"],
        })
        result = normalize_dataframe(df, "Element Name", "Element ID", "test")
        assert "skill_name_original" in result.columns
        assert "skill_name_normalized" in result.columns
        assert "normalization_flag" in result.columns

    def test_original_ids_preserved_exactly(self):
        """Source IDs must never be modified."""
        df = pd.DataFrame({
            "Element Name": ["Python"],
            "Element ID": ["KS001.ABC"],
        })
        result = normalize_dataframe(df, "Element Name", "Element ID", "test")
        assert result["Element ID"].tolist() == ["KS001.ABC"]

    def test_missing_skill_column_no_crash(self):
        """Should not crash if the named column doesn't exist."""
        df = pd.DataFrame({"Other Column": ["A"]})
        result = normalize_dataframe(df, "Nonexistent Column", None, "test")
        assert "Nonexistent Column" not in result.columns

    def test_flag_values_are_valid(self):
        df = pd.DataFrame({"Element Name": ["JS", "c#", "Totally Unknown Skill 99"]})
        result = normalize_dataframe(df, "Element Name", None, "test")
        valid_flags = {"OK", "REVIEW_NEEDED", "NO_MATCH"}
        assert set(result["normalization_flag"].unique()).issubset(valid_flags)


# ════════════════════════════════════════════════════════════════════════════
# SECTION 3: File / path tests
# ════════════════════════════════════════════════════════════════════════════

class TestDatasetPaths:
    """Verify the raw dataset files exist at the expected locations."""

    ONET_DIR = PROJECT_ROOT / "data" / "raw" / "onet"
    ESCO_DIR = PROJECT_ROOT / "data" / "raw" / "esco"

    REQUIRED_ONET = [
        "Occupation Data.xlsx",
        # NOTE: O*NET 31.0 Excel format does NOT have a generic "Skills.xlsx".
        # Skills are split into Essential Skills, Transferable Skills, Knowledge, Abilities.
        "Essential Skills.xlsx",
        "Transferable Skills.xlsx",
        "Knowledge.xlsx",
        "Abilities.xlsx",
        "Content Model Reference.xlsx",
    ]
    REQUIRED_ESCO = [
        "occupations_en.csv",
        "skills_en.csv",
        "occupationSkillRelations_en.csv",
    ]

    @pytest.mark.parametrize("filename", REQUIRED_ONET)
    def test_onet_file_exists(self, filename):
        path = self.ONET_DIR / filename
        assert path.exists(), f"Required O*NET file missing: {path}"

    @pytest.mark.parametrize("filename", REQUIRED_ESCO)
    def test_esco_file_exists(self, filename):
        path = self.ESCO_DIR / filename
        assert path.exists(), f"Required ESCO file missing: {path}"

    def test_processed_dir_exists(self):
        processed = PROJECT_ROOT / "data" / "processed"
        assert processed.exists(), "data/processed/ directory not found"

    def test_reports_dir_exists(self):
        reports = PROJECT_ROOT / "reports"
        assert reports.exists(), "reports/ directory not found"


# ════════════════════════════════════════════════════════════════════════════
# SECTION 4: Edge case tests
# ════════════════════════════════════════════════════════════════════════════

class TestEdgeCases:
    """Edge cases: empty files, single rows, all-null columns."""

    def test_single_row_dataframe(self):
        df = pd.DataFrame({"a": ["Python"], "b": [1]})
        result, log = clean_dataframe(df, "single_row")
        assert log["rows_after"] == 1

    def test_all_null_column_handled(self):
        df = pd.DataFrame({"a": [None, None], "b": ["x", "y"]})
        result, _ = clean_dataframe(df, "null_col")
        assert len(result) == 2   # rows are not empty (column b has values)

    def test_normalization_with_all_unknowns(self):
        df = pd.DataFrame({"skill": ["XYZ123", "ABC999", "QWE456"]})
        result = normalize_dataframe(df, "skill", None, "test")
        assert all(result["normalization_flag"] == "NO_MATCH")

    def test_normalization_with_all_known(self):
        df = pd.DataFrame({"skill": ["JS", "javascript", "sql"]})
        result = normalize_dataframe(df, "skill", None, "test")
        assert all(result["normalization_flag"] == "OK")
