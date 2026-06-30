# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""MCAT study-app additions (Phase 1).

This package contains the fork-specific surfaces that turn Anki into an MCAT
study tool. In Phase 1 it hosts the honest, deterministic *Memory* score
(FR-5) and a minimal panel to display it (FR-6).

Nothing in this package uses AI/LLMs: every number is a deterministic function
of real review data. Memory is the only graded score in Phase 1; Performance
and Readiness are deferred to later phases and must stay separate (blending
scores is an automatic fail per the spec).
"""
