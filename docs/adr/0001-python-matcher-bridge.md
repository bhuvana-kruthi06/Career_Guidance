# ADR 0001: Keep the matcher behind a Python subprocess bridge

- Status: Accepted
- Context: The weighted career-matching implementation and its data pipeline already run in Python. Rewriting the matcher would risk changing its formula and introduce duplicate logic.
- Decision: Spring delegates skill, match, and what-if operations to `MatcherBridge`, which invokes the existing Python process. Keep process mode as the safe default.
- Consequences: Java remains the API/security boundary and Python remains the scoring boundary. Process startup and data availability must be checked operationally; the formula is documented in `docs/MATCHING.md`.
