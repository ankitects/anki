#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""
Compare line coverage percentages between a baseline and the current run.

Usage:
    check-coverage-regression <baseline-dir> <current-dir>

    baseline-dir  Directory containing coverage-summary.json files from main
                  (e.g. out/coverage-baseline)
    current-dir   Directory containing coverage-summary.json files from the
                  current PR run (e.g. out/coverage)

Exits with code 1 if any stack's line coverage is below the baseline.

Tolerance: 0.20% — small decreases within this margin are ignored to absorb
instrumentation noise across runs. Anything beyond this is treated as a
regression. If a decrease is acceptable (e.g. dead code removed), update
the baseline by merging to main.
"""

import json
import sys
from pathlib import Path
from typing import Any, Callable, TypedDict

TOLERANCE = 0.2  # percentage points; absorbs instrumentation noise across runs
# (0.1 produced false positives: llvm-cov/coverage.py fluctuate up to ~0.15pp
#  between identical runs due to non-deterministic macro/generic instrumentation)


class Stack(TypedDict):
    name: str
    path: str
    extract: Callable[[Any], float]


STACKS: list[Stack] = [
    {
        "name": "rust",
        "path": "rust/coverage-summary.json",
        "extract": lambda d: d["data"][0]["totals"]["lines"]["percent"],
    },
    {
        "name": "python-pylib",
        "path": "python-pylib/coverage-summary.json",
        "extract": lambda d: d["totals"]["percent_covered"],
    },
    {
        "name": "python-qt",
        "path": "python-qt/coverage-summary.json",
        "extract": lambda d: d["totals"]["percent_covered"],
    },
    {
        "name": "typescript",
        "path": "typescript/coverage-summary.json",
        "extract": lambda d: d["total"]["lines"]["pct"],
    },
]


def load_pct(directory: Path, stack: Stack) -> float | None:
    path = directory / stack["path"]
    if not path.exists():
        return None
    return stack["extract"](json.loads(path.read_text()))


def main() -> int:
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <baseline-dir> <current-dir>", file=sys.stderr)
        return 2

    baseline_dir = Path(sys.argv[1])
    current_dir = Path(sys.argv[2])

    regressions: list[tuple[str, float, float]] = []

    for stack in STACKS:
        name = stack["name"]
        baseline_pct = load_pct(baseline_dir, stack)
        current_pct = load_pct(current_dir, stack)

        if baseline_pct is None:
            print(f"[{name}] no baseline — skipping")
            continue
        if current_pct is None:
            print(
                f"[{name}] coverage-summary.json not found in {current_dir}",
                file=sys.stderr,
            )
            return 2

        delta = current_pct - baseline_pct
        if delta < -TOLERANCE:
            regressions.append((name, baseline_pct, current_pct))
            print(
                f"[{name}] REGRESSION: {baseline_pct:.2f}% -> {current_pct:.2f}%"
                f"  (delta: {delta:+.2f}%, tolerance: {TOLERANCE:.2f}%)"
            )
        else:
            print(
                f"[{name}] ok: {current_pct:.2f}%"
                f"  (baseline: {baseline_pct:.2f}%, delta: {delta:+.2f}%)"
            )

    if regressions:
        names = ", ".join(r[0] for r in regressions)
        print(
            f"\n{len(regressions)} stack(s) with coverage regression: {names}\n"
            f"Configured tolerance: {TOLERANCE:.2f}%\n"
            "To accept a legitimate decrease (e.g. dead code removed),\n"
            "merge to main — this will update the baseline automatically."
        )
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
