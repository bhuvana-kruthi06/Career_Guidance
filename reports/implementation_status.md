# Implementation Status — Historical Snapshot (2026-10-25)

> This file describes the project before the 2026-10-26 backend and frontend work. See [implementation_status_2026-10-26.md](implementation_status_2026-10-26.md) for the current verified status.

**Project:** AI-Powered Personalised Career Guidance and Skill Gap Analysis Platform  
**Last updated:** 2026-10-25  
**Phase:** I — Data Processing Pipeline

---

## Legend
| Symbol | Meaning |
|--------|---------|
| ⬜ Not Started | Work has not begun |
| 🔵 In Progress | Currently being implemented |
| ✅ Implemented | Code written, not yet tested |
| 🟢 Tested | Implemented and tests verified passing |

---

## Phase I: Data Processing Pipeline  🟢 COMPLETE

| Component | Status | File(s) | Actual Result |
|-----------|--------|---------|---------------|
| Project folder structure | 🟢 Tested | All folders | Created and verified |
| O\*NET 31.0 dataset organised | 🟢 Tested | `data/raw/onet/` (46 files) | All 46 files present |
| ESCO v1.2.1 dataset organised | 🟢 Tested | `data/raw/esco/` (19 files) | All 19 files present |
| Virtual environment | 🟢 Tested | `venv/` | Python 3.15, all packages installed |
| `requirements.txt` | 🟢 Tested | `requirements.txt` | pandas 3.1, numpy 2.5, matplotlib 3.11 etc. |
| `.gitignore` | 🟢 Tested | `.gitignore` | Raw data excluded from git |
| `README.md` | 🟢 Tested | `README.md` | Full setup + attribution docs |
| **Data Inspection** | 🟢 Tested | `data_processing/inspect_data.py` | 14 O\*NET + 10 ESCO files inspected, report saved |
| **Data Cleaning** | 🟢 Tested | `data_processing/clean_data.py` | 15 files cleaned, 0 rows removed, 15 CSVs written |
| **Skill Normalisation** | 🟢 Tested | `data_processing/normalize_skills.py` | 6 files normalised, 4 flagged for review |
| **EDA** | 🟢 Tested | `data_processing/explore_data.py` | 5 charts generated, full report written |
| **Test suite** | 🟢 Tested | `tests/test_pipeline.py` | **49/49 tests passed** |
| `reports/data_analysis_report.md` | 🟢 Tested | `reports/data_analysis_report.md` | Full EDA report with real statistics |
| `reports/charts/` | 🟢 Tested | 5 PNG charts | All generated from real data |

### Processed Outputs in `data/processed/` (22 files)

| File | Rows | Notes |
|------|------|-------|
| `onet_occupation_data_clean.csv` | 1,016 | 1,016 unique occupations |
| `onet_essential_skills_clean.csv` | 18,200 | 10 skill types × 910 occ × 2 scales |
| `onet_essential_skills_normalized.csv` | 18,200 | + normalization columns |
| `onet_transferable_skills_clean.csv` | 45,500 | 25 skill types |
| `onet_transferable_skills_normalized.csv` | 45,500 | |
| `onet_knowledge_clean.csv` | 60,060 | 33 knowledge domains |
| `onet_knowledge_normalized.csv` | 60,060 | |
| `onet_abilities_clean.csv` | 94,640 | 52 ability types |
| `onet_abilities_normalized.csv` | 94,640 | |
| `onet_software_skills_clean.csv` | 31,821 | 8,753 unique tools |
| `onet_software_skills_normalized.csv` | 31,821 | |
| `onet_education_clean.csv` | 11,495 | |
| `onet_job_zones_clean.csv` | 923 | Job Zones 2–5 |
| `onet_career_interests_clean.csv` | 8,307 | RIASEC scores for 923 occupations |
| `onet_related_occupations_clean.csv` | 18,460 | |
| `esco_occupations_clean.csv` | 3,043 | 3,039 unique |
| `esco_skills_clean.csv` | 13,960 | 10,734 competences + 3,221 knowledge |
| `esco_skills_normalized.csv` | 13,960 | |
| `esco_occupation_skill_relations_clean.csv` | 126,051 | 67,600 essential + 58,451 optional |
| `esco_skill_groups_clean.csv` | 640 | |
| `esco_isco_groups_clean.csv` | 619 | |
| `normalization_flagged_review.csv` | 4 | Manual review needed |

---

## Phase II: Career Matching Engine  ⬜ Not Started

| Component | Status | Notes |
|-----------|--------|-------|
| Weighted matching algorithm design | ⬜ Not Started | Needs approval of formula |
| Skill gap calculation | ⬜ Not Started | Depends on matching algo |
| Career-skill mapping (O\*NET → unified) | ⬜ Not Started | Depends on Phase I |
| Career ranking function | ⬜ Not Started | |
| Python matching engine (REST-ready) | ⬜ Not Started | |
| MySQL schema design | ⬜ Not Started | |
| Spring Boot project setup | ⬜ Not Started | |
| REST API endpoints | ⬜ Not Started | |
| Spring Security + JWT | ⬜ Not Started | |

## Phase III: Frontend  ⬜ Not Started

| Component | Status |
|-----------|--------|
| React project setup | ⬜ Not Started |
| Student profile UI | ⬜ Not Started |
| Skill assessment UI (1–5 scale) | ⬜ Not Started |
| Career recommendation dashboard | ⬜ Not Started |
| Skill gap visualisation | ⬜ Not Started |
| Learning roadmap UI | ⬜ Not Started |
| Progress tracking UI | ⬜ Not Started |

## Phase IV: LLM Integration  ⬜ Not Started

| Component | Status | Notes |
|-----------|--------|-------|
| LLM API selection | ⬜ Not Started | Deferred by user decision |
| Prompt engineering | ⬜ Not Started | |
| AI guidance module | ⬜ Not Started | |

---

## Known Issues & Notes

| # | Issue | Status |
|---|-------|--------|
| 1 | Pandas 3.x `Pandas4Warning` on `select_dtypes` | ✅ Fixed (use `include=["object","str"]`) |
| 2 | O\*NET has no generic `Skills.xlsx` in v31.0 | ✅ Fixed — scripts updated to correct filenames |
| 3 | EDA cross-dataset overlap used wrong file | ✅ Fixed — now uses `onet_essential_skills_normalized.csv` |
| 4 | Weighted matching formula not yet defined | ⬜ Pending approval |
| 5 | ESCO–O\*NET cross-dataset linkage not official | ⬜ Documented as limitation |

---

## Test Results — 2026-10-25

```
Platform: Windows, Python 3.15.0, pytest 9.1.1
Collected: 49 tests

49 passed in 0.86s  ✅ ALL PASS
```

**Test categories covered:**
- Whitespace cleaning (5 tests)
- Empty row removal (4 tests)
- Duplicate removal (4 tests)
- Original column preservation (4 tests)
- Clean dataframe integration (2 tests)
- Alias map correctness incl. Java ≠ JavaScript (11 tests)
- Normalize dataframe (4 tests)
- Dataset file path existence (8 tests)
- Edge cases (4 tests)

---

## Next Steps (Priority Order)

1. **Propose weighted career-matching algorithm** — get approval on formula and weights
2. **Build career-skill mapping** — unify O\*NET scores into a matchable format
3. **Implement matching engine** — Python module, testable independently
4. **MySQL schema design**
5. **Spring Boot backend setup**
