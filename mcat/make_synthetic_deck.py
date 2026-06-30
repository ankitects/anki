#!/usr/bin/env python3
# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Generate a realistic synthetic AnKing-style MCAT deck (FR-2 at scale).

The real AnKing MCAT deck (~35k cards) is AnkiHub/AnkiWeb-gated and cannot be
downloaded here, so this builds a *file-backed* Anki collection (a temp
``.anki2``, not in-memory) with ~50,000 notes/cards for performance and
pipeline validation. Everything is deterministic -- NO AI; the RNG is seeded
with a fixed value so re-running produces the same deck.

Why the tags are modeled INDEPENDENTLY of ``taxonomy.json``
-----------------------------------------------------------
``taxonomy.json`` maps cards->concepts via ``tag_patterns``. If we generated
tags *from* those patterns, the coverage map would be circular (100% by
construction). Instead we model the *real* AnKing hierarchical tag scheme from
first principles:

    #AK_MCAT_V12::#B&B::01_Biochemistry::1.2_Enzymes::Kinetics

i.e. a root (``#AK_MCAT_V12::``), a section node (``#B&B`` / ``#C/P`` /
``#P/S``), then subject / chapter / subtopic levels. Coverage then becomes a
genuine test of whether the taxonomy's glob patterns actually catch these
independently-authored tags. We deliberately:

  * leave some taxonomy concepts under-represented or entirely uncovered
    (so overall coverage is < 100% and FR-5's give-up rule is exercised),
  * spread cards across many distinct leaf tags,
  * give a realistic fraction of cards FSRS memory state (stability /
    difficulty / last_review_time) so the ConceptMastery RPC has real
    retrievability to compute; fresh cards correctly have none.

Run (after the PATH / PYTHONPATH / ANKI_TEST_MODE setup -- see mcat/README.md):

    python mcat/make_synthetic_deck.py --n 50000 --out mcat/fixtures/synthetic_50k.anki2
"""

from __future__ import annotations

import argparse
import os
import random
import sys
import time

# --------------------------------------------------------------------------
# AnKing-style hierarchy, authored INDEPENDENTLY of taxonomy.json patterns.
#
# Each entry is (section_node, subject, [chapters], [subtopics]). The leaf
# subtopics are chosen to overlap *some* taxonomy concepts (so coverage is
# realistic) while leaving others uncovered. We do NOT copy the taxonomy's
# glob patterns; any match is incidental to using real MCAT vocabulary.
# --------------------------------------------------------------------------
ROOT = "#AK_MCAT_V12"

# section_node -> list of (subject_dir, [ (chapter_dir, [subtopics]) ])
HIERARCHY = {
    "#B&B": [
        (
            "01_Biochemistry",
            [
                ("1.1_Amino_Acids", ["Structure", "Classification", "Titration"]),
                ("1.2_Enzymes", ["Kinetics", "Inhibition", "Regulation"]),
                ("1.3_Protein_Structure", ["Folding", "Function", "Denaturation"]),
                ("1.4_Carbohydrate_Metabolism", ["Glycolysis", "Gluconeogenesis"]),
                ("1.5_Bioenergetics", ["Krebs", "Oxidative_Phosphorylation"]),
                ("1.6_Fatty_Acid_Metabolism", ["Beta_Oxidation", "Synthesis"]),
            ],
        ),
        (
            "02_Molecular_Biology",
            [
                ("2.1_Nucleic_Acid_Structure", ["DNA", "RNA"]),
                ("2.2_DNA_Replication", ["Polymerases", "Repair"]),
                ("2.3_Transcription", ["Initiation", "Processing"]),
                ("2.4_Translation", ["Ribosome", "Genetic_Code"]),
            ],
        ),
        (
            "03_Cell_Biology",
            [
                ("3.1_Plasma_Membrane", ["Transport", "Fluid_Mosaic"]),
                ("3.2_Organelle", ["Mitochondria", "ER"]),
                ("3.3_Cytoskeleton", ["Microtubules", "Actin"]),
                ("3.4_Cell_Cycle", ["Mitosis", "Checkpoints"]),
                # Note: cell differentiation / apoptosis intentionally sparse.
            ],
        ),
        (
            "04_Physiology",
            [
                ("4.1_Nervous_System", ["Neuron", "Action_Potential", "Neurotransmitter"]),
                ("4.2_Endocrine", ["Hormone", "Feedback"]),
                ("4.3_Cardiovascular", ["Heart", "Vessels"]),
                ("4.4_Respiratory_System", ["Gas_Exchange", "Mechanics"]),
                ("4.5_Renal", ["Nephron", "Filtration"]),
                ("4.6_Digestive", ["Enzymes", "Absorption"]),
                ("4.7_Immune_System", ["Innate", "Adaptive"]),
                ("4.8_Musculoskeletal", ["Muscle", "Bone"]),
                ("4.9_Reproductive_System", ["Gametogenesis", "Cycle"]),
            ],
        ),
        (
            "05_Genetics",
            [
                ("5.1_Mendelian", ["Punnett", "Dihybrid"]),
                ("5.2_Meiosis", ["Recombination", "Nondisjunction"]),
                ("5.3_Evolution", ["Selection", "Hardy_Weinberg"]),
                ("5.4_Mutation", ["Point", "Frameshift"]),
            ],
        ),
        (
            "06_Microbiology",
            [
                ("6.1_Bacteria", ["Structure", "Growth"]),
                ("6.2_Virus", ["Lifecycle", "Viral_Genetics"]),
            ],
        ),
    ],
    "#C/P": [
        (
            "07_General_Chemistry",
            [
                ("7.1_Atomic_Structure", ["Electronic_Structure", "Periodic_Table"]),
                ("7.2_Bonding", ["Lewis_Structure", "Molecular_Geometry", "VSEPR"]),
                ("7.3_Thermodynamics", ["Enthalpy", "Entropy", "Gibbs"]),
                ("7.4_Kinetics", ["Reaction_Rate", "Chemical_Equilibrium"]),
                ("7.5_Acid_Base", ["pH", "Buffer", "Titration"]),
                ("7.6_Solution_Chemistry", ["Solubility", "Colligative"]),
            ],
        ),
        (
            "08_Organic_Chemistry",
            [
                ("8.1_Functional_Group", ["Alcohol", "Carbonyl", "Carboxylic_Acid"]),
                ("8.2_Reaction_Mechanism", ["SN1_SN2", "Addition"]),
                ("8.3_Stereochemistry", ["Chirality", "Isomers"]),
                ("8.4_Separation", ["Chromatography", "Distillation", "Extraction"]),
                ("8.5_Spectroscopy", ["NMR", "IR"]),
            ],
        ),
        (
            "09_Physics",
            [
                ("9.1_Kinematics", ["Motion", "Projectile"]),
                ("9.2_Newton_Law", ["Force", "Friction"]),
                ("9.3_Work_Energy", ["Power", "Conservation"]),
                ("9.4_Fluids", ["Hydrostatic", "Bernoulli"]),
                ("9.5_Electrostatics", ["Electric_Field", "Circuit"]),
                ("9.6_Optics", ["Geometric_Optics", "Light"]),
                ("9.7_Wave", ["Sound", "Doppler"]),
                # Note: magnetism / nuclear intentionally absent.
            ],
        ),
    ],
    "#P/S": [
        (
            "10_Sensation_Perception",
            [
                ("10.1_Sensation", ["Vision", "Hearing"]),
                ("10.2_Perception", ["Signal_Detection", "Gestalt"]),
            ],
        ),
        (
            "11_Cognition",
            [
                ("11.1_Memory_Psych", ["Encoding", "Retrieval"]),
                ("11.2_Cognition", ["Problem_Solving", "Intelligence"]),
                ("11.3_Consciousness", ["Sleep", "Attention"]),
            ],
        ),
        (
            "12_Learning",
            [
                ("12.1_Conditioning", ["Classical_Conditioning", "Operant"]),
                ("12.2_Learning_Psych", ["Observational", "Habituation"]),
            ],
        ),
        (
            "13_Social_Psychology",
            [
                # Deliberately thin: self-identity / social-thinking / social-
                # interaction (taxonomy 8A-8C) left mostly uncovered.
                ("13.1_Group_Behavior", ["Conformity", "Socialization"]),
                ("13.2_Sociology", ["Social_Structure", "Culture"]),
            ],
        ),
        # Note: Foundational Concept 9B (demographics), 10A (social inequality),
        # 6C (emotion/stress/motivation), and 7A (personality/disorders) are
        # intentionally NOT represented here, so coverage stays < 100% and the
        # give-up rule is honestly exercised.
    ],
}


def _build_leaf_tags() -> list[tuple[str, float]]:
    """Flatten the hierarchy into (full_tag, weight) leaves.

    ``weight`` biases how many cards land on each leaf so the distribution is
    skewed (a few big chapters, a long tail of small ones), like a real deck.
    """
    rng = random.Random(20260630)  # fixed seed for the weight skew too
    leaves: list[tuple[str, float]] = []
    for section_node, subjects in HIERARCHY.items():
        for subject_dir, chapters in subjects:
            for chapter_dir, subtopics in chapters:
                for sub in subtopics:
                    full = f"{ROOT}::{section_node}::{subject_dir}::{chapter_dir}::{sub}"
                    # Skew: most leaves modest, a few heavy.
                    weight = rng.choice([1, 1, 1, 2, 2, 3, 5])
                    leaves.append((full, float(weight)))
    return leaves


# Extra "noise" tags every AnKing deck carries (exam sources, flags) that map
# to NO taxonomy concept -- these create realistic unmapped cards.
NOISE_TAGS = [
    "#AK_MCAT_V12::#AK_Update::V12",
    "#AK_MCAT_V12::#Exam::AAMC_FL1",
    "#AK_MCAT_V12::#Exam::Kaplan",
    "#AK_MCAT_V12::#Resource::KhanAcademy",
    "leech",
    "marked",
]


def generate(n: int, out: str, seed: int = 20260630) -> dict:
    """Create a file-backed collection at ``out`` with ``n`` cards. Returns stats."""
    from anki.cards_pb2 import FsrsMemoryState
    from anki.collection import Collection

    out = os.path.abspath(out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    if os.path.exists(out):
        os.remove(out)

    rng = random.Random(seed)
    leaves = _build_leaf_tags()
    leaf_tags = [t for t, _ in leaves]
    leaf_weights = [w for _, w in leaves]

    col = Collection(out)
    basic = col.models.by_name("Basic")
    deck_id = col.decks.id("MCAT::Synthetic")

    now = int(time.time())
    # ~60% of cards have FSRS memory state (reviewed at least once); the rest
    # are fresh "new" cards with no memory state -> retrievability None.
    reviewed_fraction = 0.60

    t0 = time.time()
    note_ids: list[int] = []
    cards_with_state = 0

    # Add notes in a transaction-friendly loop. add_note commits per note via
    # the backend; for 50k that is the dominant cost but acceptable here.
    for i in range(n):
        note = col.new_note(basic)
        note["Front"] = f"Synthetic MCAT card {i}: what is the key fact?"
        note["Back"] = f"Deterministic answer body for card {i}."

        # Pick 1 primary leaf tag (weighted), occasionally a 2nd cross-cutting
        # leaf, plus an occasional noise tag.
        primary = rng.choices(leaf_tags, weights=leaf_weights, k=1)[0]
        tags = [primary]
        if rng.random() < 0.18:
            tags.append(rng.choices(leaf_tags, weights=leaf_weights, k=1)[0])
        if rng.random() < 0.12:
            tags.append(rng.choice(NOISE_TAGS))
        note.tags = list(dict.fromkeys(tags))  # dedupe, keep order

        col.add_note(note, deck_id)
        note_ids.append(note.id)

    add_secs = time.time() - t0

    # Second pass: give a realistic fraction of cards FSRS memory state, in
    # bulk via update_cards (much faster than per-card update_card).
    t1 = time.time()
    batch = []
    BATCH = 2000
    for nid in note_ids:
        for cid in col.card_ids_of_note(nid):
            if rng.random() < reviewed_fraction:
                card = col.get_card(cid)
                # Spread stability so retrievability spans the 0..1 range and a
                # realistic minority clears the 0.9 mastery threshold.
                stability = rng.choice([1.0, 3.0, 8.0, 20.0, 60.0, 150.0])
                difficulty = round(rng.uniform(2.0, 9.0), 2)
                card.memory_state = FsrsMemoryState(
                    stability=stability, difficulty=difficulty
                )
                # last review between 0 and 90 days ago.
                days_ago = rng.randint(0, 90)
                card.last_review_time = now - days_ago * 86400
                card.ivl = max(1, int(stability))
                card.reps = rng.randint(1, 12)
                card.due = rng.randint(1, 365)
                batch.append(card)
                cards_with_state += 1
                if len(batch) >= BATCH:
                    col.update_cards(batch, skip_undo_entry=True)
                    batch = []
    if batch:
        col.update_cards(batch, skip_undo_entry=True)
    state_secs = time.time() - t1

    total_cards = col.card_count()
    note_count = col.note_count()
    col.close()

    return {
        "out": out,
        "requested": n,
        "notes": note_count,
        "cards": total_cards,
        "cards_with_memory_state": cards_with_state,
        "distinct_leaf_tags": len(leaf_tags),
        "add_seconds": round(add_secs, 1),
        "state_seconds": round(state_secs, 1),
        "total_seconds": round(add_secs + state_secs, 1),
    }


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Generate a synthetic AnKing-style MCAT deck.")
    ap.add_argument("--n", type=int, default=50000, help="Number of cards/notes (default 50000).")
    ap.add_argument(
        "--out",
        default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures", "synthetic_50k.anki2"),
        help="Output .anki2 path (default mcat/fixtures/synthetic_50k.anki2).",
    )
    ap.add_argument("--seed", type=int, default=20260630, help="RNG seed (deterministic).")
    args = ap.parse_args(argv)

    stats = generate(args.n, args.out, args.seed)
    print("Synthetic deck generated:")
    for k, v in stats.items():
        print(f"  {k}: {v}")
    print(f"\nDeck path: {stats['out']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
