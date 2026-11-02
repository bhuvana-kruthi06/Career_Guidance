# Career match score

The recommendation formula is implemented in `data_processing/matching_engine.py`. This document describes that implementation; it does not change it.

For each occupation, the matcher considers the occupation's skill rows. In quick-profile mode it scores only skills the person rated (`assessed_only=true`). A rating from 1 through 5 is linearly converted to the O*NET level scale from 0 through 7:

```text
onet_level = (rating - 1) × (7 / 4)
```

For every included skill with positive importance and a positive required level, the skill coverage is `min(person_level, required_level) / required_level`. The base score is the importance-weighted average of those coverage values, multiplied by 100 and rounded to two decimal places. Skills where the person is below the requirement appear as gaps. Gap priority is `(required_level - person_level) × importance`; gaps are sorted from highest priority to lowest.

The matcher adds a small RIASEC interest bonus for overlaps between the person's interest codes and an occupation's top three codes. The bonus uses the existing configured per-match value and cap. The final score is `min(100, base_score + riasec_bonus)`, rounded to two decimals. Results are sorted by this final score and truncated to `top_n`.

The score is a profile similarity measure based on the local O*NET data. It is not a probability of being hired, job performance, or career success. Ratings are self-reported, and the result depends on which skills were assessed.

The API result already includes `base_score`, `riasec_bonus`, `skills_assessed`, `skills_total`, `strong_skills`, and `skill_gaps`; the frontend displays these returned values without recalculating the formula.
