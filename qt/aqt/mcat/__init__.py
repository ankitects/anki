# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""MCAT kernel additions.

This fork turns Anki into the **kernel** of an MCAT study tool -- not an
all-in-one app. Anki itself is left essentially unchanged (spaced-repetition
review is the core); this package adds two things on top:

* **Practice-test ingestion** (:mod:`aqt.mcat.ingest`,
  :mod:`aqt.mcat.practice_tests`) -- the student uploads/annotates the practice
  tests they have already taken, tagging each question with the MCAT concept it
  tested and whether they got it right. This feeds the Rust engine's per-concept
  **Need-to-Review (NTR)** signal, re-prioritising review toward weak concepts.
* **Predictions & recommendations** (:mod:`aqt.mcat.panel`) -- three separate,
  never-blended outputs: a projected MCAT **Readiness** score, an honest FSRS
  **Memory** score, and the per-concept NTR chart that recommends what to study
  next.

Nothing in this package uses AI/LLMs: every number is a deterministic function
of real review data and ingested practice-test results. The three scores stay
separate (blending them is an automatic fail per the spec).
"""
