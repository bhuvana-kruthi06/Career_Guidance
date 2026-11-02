# Current limitations

- Career-match scores describe similarity to the student's self-reported skill and interest profile; they are not hiring probabilities or guarantees of career fit.
- Skill ratings are self-reported and not validated by an assessment instrument.
- Crosswalks between O*NET and ESCO skill vocabularies are approximate.
- Career metadata and matching require locally generated `data/processed/` files. Those files are excluded from version control; a clean-clone distribution and compose workflow is still pending a licensing/data-delivery decision.
- Learning links are curated references. Terminal link checks may be unavailable where outbound network access is blocked; users should confirm current course availability and terms.
- MySQL persistence is optional. Automated tests use H2 in MySQL mode; a live MySQL restart and containerized production-style deployment have not been verified.
- No hosted deployment, telemetry, or external AI/LLM service is included.
- The local account flow has not had an independent penetration test or formal accessibility audit.
