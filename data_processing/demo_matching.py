"""
demo_matching.py
----------------
Demonstrates the career matching engine using the PPT's example student
profile (Slide 9) and additional test profiles.

This is a READ-ONLY demo — it does not modify any data files.

USAGE:
    python data_processing/demo_matching.py
"""

import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

from data_processing.matching_engine import CareerMatcher, MATCHING_CONFIG


def run_demo() -> None:
    print("=" * 70)
    print("  CAREER MATCHING ENGINE — DEMO")
    print("  Using PPT Phase-I example profiles (Slide 9)")
    print("=" * 70)

    matcher = CareerMatcher()

    # ── Profile 1: PPT Example (Slide 9) ─────────────────────────────────────
    # The PPT shows: Java 4/5, SQL 4/5, Spring Boot 1/5, Git 3/5
    # These map to O*NET Essential/Transferable element names
    # Note: O*NET does not have "Java" or "Spring Boot" as Essential Skill
    # elements — those are Software Skills. The Essential/Transferable skills
    # are broader cognitive/behavioural categories. So this demo shows what
    # the engine can score using the actual O*NET taxonomy.
    print("\n" + "-" * 70)
    print("PROFILE 1 — PPT Example Student (Software Developer background)")
    print("  Skills rated on 1–5 scale (1=Beginner, 5=Expert)")
    print("-" * 70)

    ppt_profile = {
        # O*NET Essential Skills (Element Names — exact match required)
        "Reading Comprehension":  4,
        "Active Listening":       3,
        "Writing":                3,
        "Speaking":               3,
        "Mathematics":            4,
        "Critical Thinking":      4,
        "Active Learning":        3,
        "Learning Strategies":    3,
        "Monitoring":             2,
        "Science":                2,

        # O*NET Transferable Skills
        "Programming":            4,
        "Systems Analysis":       3,
        "Systems Evaluation":     3,
        "Technology Design":      2,
        "Troubleshooting":        3,
        "Operations Analysis":    2,

        # O*NET Knowledge
        "Computers and Electronics": 4,
        "Mathematics":               4,
        "Engineering and Technology": 3,
    }

    print("Student profile:")
    for skill, rating in ppt_profile.items():
        bar = "█" * rating + "░" * (5 - rating)
        print(f"  {skill:<35} {bar} {rating}/5")

    results = matcher.match(
        student_skills=ppt_profile,
        student_interests=["I", "C"],  # Investigative + Conventional (tech-oriented)
        top_n=5,
    )

    print()
    print(matcher.format_results(results))

    # ── Profile 2: Data Analyst background ───────────────────────────────────
    print("\n" + "-" * 70)
    print("PROFILE 2 — Data / Analytics background")
    print("-" * 70)

    data_profile = {
        "Reading Comprehension":  5,
        "Mathematics":            5,
        "Critical Thinking":      4,
        "Active Learning":        4,
        "Writing":                3,
        "Speaking":               3,
        "Science":                4,
        "Active Listening":       3,
        "Monitoring":             4,
        "Learning Strategies":    3,

        "Programming":            3,
        "Systems Analysis":       4,
        "Operations Analysis":    4,
        "Systems Evaluation":     3,
        "Troubleshooting":        2,

        "Computers and Electronics":   4,
        "Mathematics":                 5,
        "Engineering and Technology":  3,
        "Administration and Management": 2,
    }

    results2 = matcher.match(
        student_skills=data_profile,
        student_interests=["I", "R"],  # Investigative + Realistic
        top_n=5,
    )
    print(matcher.format_results(results2))

    # ── Scale conversion reference ────────────────────────────────────────────
    print("\n" + "-" * 70)
    print("SCALE MAPPING REFERENCE (Student 1–5 → O*NET Level 0–7)")
    print("-" * 70)
    for rating in [1, 2, 3, 4, 5]:
        onet_lv = CareerMatcher.student_to_onet_level(rating)
        labels = {1: "Beginner", 2: "Elementary", 3: "Intermediate",
                  4: "Advanced", 5: "Expert"}
        print(f"  Student {rating}/5 ({labels[rating]:<13}) → O*NET Level {onet_lv:.2f}/7.00")

    print("\n" + "=" * 70)
    print("  DEMO COMPLETE")
    print("  Results above are real outputs from O*NET 31.0 data.")
    print("  Scores are NOT probabilities of employment or career success.")
    print("=" * 70)


if __name__ == "__main__":
    run_demo()
