# MCAT Concept Taxonomy & Coverage Map (FR-2)

This directory owns the **concept taxonomy** and the **coverage map** for the
MCAT fork of Anki. It is the canonical concept list that every later feature
(NTR scheduling, Memory score, lessons, questions) annotates against, and it
provides the coverage signal that gates FR-5's give-up rule.

It is self-contained and standard-library only. It does **not** touch
`proto/`, `rslib/`, `qt/`, `ts/`, or `pylib/`, and it does not invoke the
build system.

## Files

| File                            | Purpose                                                                             |
| ------------------------------- | ----------------------------------------------------------------------------------- |
| `taxonomy.json`                 | Versioned concept taxonomy. Shared contract with the engine agent.                  |
| `coverage.py`                   | Computes the coverage map. Importable + CLI runnable.                               |
| `fixtures/sample_cards.json`    | Synthetic AnKing-style cards spanning several concepts (some deliberately missing). |
| `fixtures/sample_coverage.json` | Coverage report produced from the sample cards.                                     |

## Taxonomy source: AAMC Foundational Concepts

`taxonomy.json` is anchored to the official **AAMC MCAT content outline (2015
exam onward)**: the 10 Foundational Concepts and their content categories.

- **Bio/Biochem** — FC1 (1A–1D), FC2 (2A–2C), FC3 (3A–3B)
- **Chem/Phys** — FC4 (4A–4E), FC5 (5A–5E)
- **Psych/Soc** — FC6 (6A–6C), FC7 (7A–7C), FC8 (8A–8C), FC9 (9A–9B), FC10 (10A)
- **CARS** — no content outline, so it is **intentionally absent** from the
  taxonomy (there is nothing to map cards to; CARS is a reasoning section).

Total: **31 content categories** across 3 examinable content sections.

Each concept has a `topic_weight` (default `1.0`). A handful of high-yield
categories are weighted slightly higher to bias the future NTR queue and the
weighted-coverage metric toward where MCAT points concentrate:

- `1A` Proteins/amino acids, `1D` metabolism/bioenergetics — `1.3`
- `1B` gene→protein, `3A` nervous/endocrine, `3B` organ systems, `5D` orgo/biomolecules — `1.2`
- A few low-frequency categories (`2B`, `4D`, `5C`, `8A`–`8C`) — `0.9`

These weights are a starting heuristic, not gospel; they are easy to revise in
one place (`topic_weight` in `taxonomy.json`) and the coverage report reports
both unweighted and weighted coverage so the effect is visible.

### Shared contract shape

```json
{
    "version": "mcat-aamc-2015-v1",
    "concepts": [
        {
            "id": "1A",
            "name": "...",
            "section": "Bio/Biochem",
            "foundational_concept": "1",
            "topic_weight": 1.3,
            "tag_patterns": ["*Amino*Acid*", "*Protein*Structure*"]
        }
    ]
}
```

The engine agent consumes `id`, `section`, `topic_weight`, and the per-card
concept mapping. Bump `version` on any change to `id`s, sections, or weights.

## Tag-pattern matching rule

**Key decision:** AnKing MCAT is organized by **hierarchical tags, not
subdecks.** So concept membership is derived from a card's tag strings, not its
deck. Each concept lists `tag_patterns`; a card maps to a concept if **any** of
the card's full hierarchical tag strings matches **any** of that concept's
patterns.

Matching is:

- **Case-insensitive.**
- **Glob:** `*` matches any run of characters, including the `::` tag
  separators (e.g. `*Amino*Acid*` matches
  `#AK_MCAT_V12::#B&B::01_Biochemistry::Amino_Acids::Structure`).
- A pattern with **no** wildcard is treated as a **substring** match.
- One card can map to **many** concepts (cards with multiple tags, or a tag
  matching multiple patterns).

This is implemented in `coverage.py::tag_matches_concept` /
`card_concepts`. The same matcher is what the engine should use to attribute
cards to concepts.

## Coverage map

A concept is **covered** if **≥ 1 card** maps to it. The report gives:

- per-concept card count + covered flag,
- per-section coverage % (covered concepts / total concepts in section),
- overall coverage % and a `weighted_coverage_pct` (fraction of total
  `topic_weight` that is covered — so skipping a high-yield section hurts more),
- the list of `uncovered_concepts` (the reported coverage gaps, per PRD §10).

### Regenerating the coverage map

From the repo root:

```bash
python mcat/coverage.py \
  --cards mcat/fixtures/sample_cards.json \
  --taxonomy mcat/taxonomy.json \
  --out mcat/fixtures/sample_coverage.json
```

`--cards` accepts three source types:

- a **JSON fixture**: `[{"cardId": <id>, "tags": ["...", ...]}]`
- an Anki **`.anki2`/`.anki21`** collection (read read-only via stdlib
  `sqlite3`; no Anki runtime needed),
- an **`.apkg`** package (the inner collection is extracted and read).

`coverage.py` is also importable:

```python
from mcat import coverage
tax = coverage.load_taxonomy("mcat/taxonomy.json")
cards = coverage.load_cards("mcat/fixtures/sample_cards.json")
report = coverage.compute_coverage(cards, tax)
```

Key function signatures:

- `load_taxonomy(path) -> Taxonomy`
- `load_cards(path) -> list[Card]` (dispatches on `.json` / `.anki2` / `.apkg`)
- `tag_matches_concept(tag: str, concept: Concept) -> bool`
- `card_concepts(card: Card, taxonomy: Taxonomy) -> list[str]`
- `compute_coverage(cards: Iterable[Card], taxonomy: Taxonomy) -> dict`

## How this feeds FR-5's give-up rule

FR-5 requires the app to **abstain** from showing a Memory score until it has
enough evidence. Topic coverage is half of that gate. The coverage report's
`overall.coverage_pct` (and/or `per_section` for a stricter per-section gate)
is compared against the threshold:

> **Give-up threshold (default, OD-2):** show **no** Memory score until
> **≥ 50% topic coverage AND ≥ 200 graded reviews.**

The coverage map supplies the topic-coverage half; the review count comes from
the engine's review log. A 35k-card deck that skips a whole high-weight section
should not clear the bar — hence the `weighted_coverage_pct`, which makes a
missed high-yield section visibly drag coverage down.

## AnKing MCAT deck import (deferred this round)

Full import of the ~35k-card AnKing MCAT deck is **deferred** — not needed to
prove FR-2, and the owner asked not to download/import it now.

When it lands:

- **Where:** the AnKing MCAT deck is distributed as a normal Anki **`.apkg`**
  (community deck; AnKingMed / AnKing Step decks distribution). It imports
  through Anki's standard package importer with no special handling.
- **Concept source:** after import, concepts come **from the cards' existing
  hierarchical tags** via this taxonomy's `tag_patterns` — no per-card manual
  tagging. Run `coverage.py --cards <collection>.anki2` (or the `.apkg`) to
  produce the real coverage map.
- The script already supports `.anki2`/`.apkg` so the same code path works
  on the real deck with no changes.

## Assumptions & risks (per PRD §10)

- **Tag scheme assumption.** The `tag_patterns` are modeled on the AnKing
  `#AK_MCAT_V12::#B&B::… / #C/P::… / #P/S::…` hierarchical convention. The exact
  tag strings (version suffix `V12`, leaf naming, separators) may differ in the
  actual current deck. Because patterns are **case-insensitive globs**, minor
  naming differences usually still match, but **the patterns must be validated
  against the real deck before relying on the coverage numbers.**
- **Coverage gaps are expected and reported.** Per §10, gaps are acceptable as
  long as they are reported — `uncovered_concepts` does exactly this. With the
  synthetic fixture, Psych/Soc lands at ~42% (below 50%), deliberately
  demonstrating the abstain case.
- **Substring/glob false positives.** Broad patterns (e.g. `*Wave*`, `*Bias*`)
  could over-match unrelated tags. Patterns are kept reasonably specific, but a
  validation pass on the real deck should check for mis-attributed cards.
- **One card → many concepts.** A card with multiple tags can count toward
  several concepts. This is intentional (MCAT cards are often cross-cutting) but
  means per-concept card counts are not mutually exclusive.
- **Taxonomy churn risk.** If the real AnKing tags do not map cleanly to AAMC
  categories, FR-2/FR-3 could slip (§10). The taxonomy is **versioned**
  (`mcat-aamc-2015-v1`) so changes are traceable; lock it early and bump the
  version on edits.

## Sample run result

From `fixtures/sample_cards.json` (47 cards, 3 intentionally unmapped):

- Overall: **22/31 concepts covered = 70.97%**, weighted **73.6%**.
- Bio/Biochem **100%** (9/9), Chem/Phys **80%** (8/10),
  Psych/Soc **41.67%** (5/12).
- Uncovered: `4D, 5C, 6C, 7A, 7B, 8A, 8B, 8C, 10A`.
- Psych/Soc < 50% demonstrates the give-up abstain condition.

## Demo seed data (try NTR + the Memory score live)

`seed_demo.py` builds a **small, self-contained demo collection** so you can see
the per-concept NTR diagram and the Memory score in the running app, without
touching your real collection. It writes a throwaway Anki _base folder_ with a
single "User 1" profile, seeding:

- cards across ~21 concepts, each assigned a recall band (strong/medium/weak/very
  weak) so per-concept NTR spans a wide, ordered range;
- **practice-question stats** that deliberately contrast card recall — some
  strong-on-cards concepts get weak questions (NTR pushed up) and some
  weak-on-cards concepts get strong questions (NTR pulled down), so the FR-9
  blend is visible;
- real `revlog` rows so `graded_reviews` clears FR-5's ≥200 threshold and the
  Memory score shows a number + range instead of abstaining.

Everything is deterministic (fixed seed); NO AI. Seed it, then launch the app
against the demo base:

```
just mcat-seed
just mcat-run-demo
```

`mcat-run-demo` points Anki at the demo base via the `ANKI_BASE` env var (more
reliable than forwarding `-b` through `just`). It opens a **separate "User 1"
profile inside `mcat/fixtures/demo_base`, so your real collection is untouched**.
Equivalent manual launch:

```powershell
$env:ANKI_BASE = "$PWD\mcat\fixtures\demo_base"; just run   # PowerShell
```

Then open **Tools → MCAT Dashboard → Memory & NTR** (or **Tools → MCAT Memory
Score**). The script also prints the expected coverage %, Memory score, and the
top/bottom NTR concepts — computed with the real engine RPC — so you can confirm
the app matches. A representative run: **67.7% coverage**, **Memory 64% (likely
60–68%, medium confidence)**, highest NTR on weak no-question concepts, and
strong-recall concepts pushed up the list by poor question accuracy.
