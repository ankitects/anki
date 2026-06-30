#!/usr/bin/env python3
# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""One-command benchmark for the MCAT concept engine (spec 7h / 10).

Loads a (synthetic) MCAT collection, builds the ``ConceptTaxonomy`` proto from
``mcat/taxonomy.json``, and times the two concept RPCs against the whole
collection:

  * ``concept_aware_queue(taxonomy, search="")`` -- the NTR re-ordering query.
  * ``concept_mastery(taxonomy, search="")``     -- the dashboard mastery query.

Each is run a warm-up plus N timed iterations; we report p50 / p95 / worst
(max) latency in ms, plus the row/card counts. Everything is deterministic --
NO AI.

This is the single-command benchmark. Run it (after the PATH / PYTHONPATH /
ANKI_TEST_MODE setup -- see mcat/README.md):

    python mcat/bench.py --deck mcat/fixtures/synthetic_50k.anki2

The taxonomy->proto loader below is a deliberate copy of the logic in
``qt/aqt/mcat/integration.py::load_taxonomy``; we cannot import ``aqt``
headlessly, and this file is allowed to own its own copy under ``mcat/``.
"""

from __future__ import annotations

import argparse
import json
import os
import statistics
import sys
import time

DEFAULT_TAXONOMY = os.path.join(os.path.dirname(os.path.abspath(__file__)), "taxonomy.json")
DEFAULT_DECK = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "fixtures", "synthetic_50k.anki2"
)


def build_taxonomy_proto(path: str):
    """Build a ``ConceptTaxonomy`` proto from taxonomy.json.

    Mirrors ``qt/aqt/mcat/integration.py::load_taxonomy`` (kept as a local copy
    because aqt cannot be imported headlessly). Returns ``(proto, ids)``.
    """
    from anki import concepts_pb2

    data = json.loads(open(path, encoding="utf-8").read())
    concepts = data.get("concepts", [])
    rules = [
        concepts_pb2.ConceptRule(
            id=c["id"],
            topic_weight=float(c.get("topic_weight", 1.0)),
            tag_patterns=list(c.get("tag_patterns", [])),
        )
        for c in concepts
    ]
    ids = tuple(c["id"] for c in concepts)
    return concepts_pb2.ConceptTaxonomy(rules=rules), ids


def _percentile(samples: list[float], pct: float) -> float:
    """Nearest-rank percentile in ms (samples already in ms)."""
    if not samples:
        return 0.0
    ordered = sorted(samples)
    k = max(0, min(len(ordered) - 1, int(round(pct / 100.0 * (len(ordered) - 1)))))
    return ordered[k]


def time_rpc(fn, iterations: int, warmup: int) -> tuple[list[float], int]:
    """Call ``fn`` repeatedly; return (per-call ms samples, row count of last call)."""
    rows = 0
    for _ in range(warmup):
        result = fn()
        rows = len(result)
    samples: list[float] = []
    for _ in range(iterations):
        t = time.perf_counter()
        result = fn()
        samples.append((time.perf_counter() - t) * 1000.0)
        rows = len(result)
    return samples, rows


def run(deck: str, taxonomy_path: str, iterations: int, warmup: int) -> dict:
    from anki.collection import Collection

    deck = os.path.abspath(deck)
    if not os.path.exists(deck):
        raise FileNotFoundError(f"Deck not found: {deck}")

    taxonomy, ids = build_taxonomy_proto(taxonomy_path)

    col = Collection(deck)
    try:
        card_count = col.card_count()
        note_count = col.note_count()

        def queue_fn():
            return col._backend.concept_aware_queue(taxonomy=taxonomy, search="")

        def mastery_fn():
            return col._backend.concept_mastery(taxonomy=taxonomy, search="")

        queue_samples, queue_rows = time_rpc(queue_fn, iterations, warmup)
        mastery_samples, mastery_rows = time_rpc(mastery_fn, iterations, warmup)
    finally:
        col.close()

    def summarize(samples: list[float], rows: int) -> dict:
        return {
            "rows": rows,
            "p50_ms": round(_percentile(samples, 50), 2),
            "p95_ms": round(_percentile(samples, 95), 2),
            "max_ms": round(max(samples), 2),
            "mean_ms": round(statistics.mean(samples), 2),
        }

    return {
        "deck": deck,
        "taxonomy_version": json.loads(open(taxonomy_path, encoding="utf-8").read())["version"],
        "concepts": len(ids),
        "card_count": card_count,
        "note_count": note_count,
        "iterations": iterations,
        "warmup": warmup,
        "concept_aware_queue": summarize(queue_samples, queue_rows),
        "concept_mastery": summarize(mastery_samples, mastery_rows),
    }


def print_table(report: dict) -> None:
    print()
    print("=" * 72)
    print("MCAT concept-engine benchmark (spec 7h / 10)")
    print("=" * 72)
    print(f"Deck:             {report['deck']}")
    print(f"Taxonomy:         {report['taxonomy_version']} ({report['concepts']} concepts)")
    print(f"Cards / notes:    {report['card_count']} / {report['note_count']}")
    print(f"Iterations:       {report['iterations']} timed (+{report['warmup']} warm-up)")
    print("-" * 72)
    hdr = f"{'RPC':<22}{'rows':>8}{'p50 ms':>10}{'p95 ms':>10}{'max ms':>10}{'mean ms':>10}"
    print(hdr)
    print("-" * 72)
    for key, label in [
        ("concept_aware_queue", "ConceptAwareQueue"),
        ("concept_mastery", "ConceptMastery"),
    ]:
        s = report[key]
        print(
            f"{label:<22}{s['rows']:>8}{s['p50_ms']:>10}{s['p95_ms']:>10}"
            f"{s['max_ms']:>10}{s['mean_ms']:>10}"
        )
    print("=" * 72)
    # Spec 10 interpretation: the relevant first-load dashboard target is
    # p95 < 1000 ms for ConceptMastery. The <50ms button-press target is the
    # answer/grade path, NOT these batch queries.
    m_p95 = report["concept_mastery"]["p95_ms"]
    verdict = "PASS" if m_p95 < 1000 else "FAIL"
    print(
        f"Spec 10 dashboard first-load target (ConceptMastery p95 < 1000 ms): "
        f"{m_p95} ms -> {verdict}"
    )
    print("(Note: spec 10's button-press p95 < 50 ms is the answer path, not these batch RPCs.)")
    print()


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Benchmark MCAT concept RPCs (spec 7h/10).")
    ap.add_argument("--deck", default=DEFAULT_DECK, help="Path to .anki2 deck (default synthetic_50k).")
    ap.add_argument("--taxonomy", default=DEFAULT_TAXONOMY, help="Path to taxonomy.json.")
    ap.add_argument("--iterations", type=int, default=40, help="Timed iterations per RPC (default 40).")
    ap.add_argument("--warmup", type=int, default=5, help="Warm-up iterations per RPC (default 5).")
    ap.add_argument("--out", default=None, help="Optional path to write the JSON report.")
    args = ap.parse_args(argv)

    report = run(args.deck, args.taxonomy, args.iterations, args.warmup)
    print_table(report)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(json.dumps(report, indent=2) + "\n")
        print(f"Wrote JSON report: {os.path.abspath(args.out)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
