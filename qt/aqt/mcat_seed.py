# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""MCAT fork: seed a fresh collection with the MileDown deck and run FSRS.

On collection load, if the collection has no cards yet, import the bundled
MileDown `.apkg` (preserving its scheduling/revlog) and then run FSRS so each
card's memory state / retrievability is populated for the topic-mastery
dashboard. Existing (non-empty) collections are left untouched. See
specs/PRD1.md §0.5 and §11.
"""

from __future__ import annotations

import os
from pathlib import Path

from anki.collection import (
    Collection,
    ImportAnkiPackageOptions,
    ImportAnkiPackageRequest,
)
from anki.decks import (
    DEFAULT_DECK_ID,
    UpdateDeckConfigs,
    UpdateDeckConfigsMode,
)

# Name of the deck file dropped at the repo root for the demo. Override the
# whole path with the ANKI_SEED_DECK environment variable.
_DECK_FILENAME = "MCAT_Milesdown.apkg"


def _seed_deck_path() -> Path | None:
    """Locate the bundled MileDown deck, or None if it isn't present."""
    override = os.environ.get("ANKI_SEED_DECK")
    if override:
        path = Path(override)
        return path if path.is_file() else None
    # .../qt/aqt/mcat_seed.py -> parents[2] is the repo root.
    candidate = Path(__file__).resolve().parents[2] / _DECK_FILENAME
    return candidate if candidate.is_file() else None


def seed_if_empty(col: Collection) -> None:
    """Import the MileDown deck + run FSRS, but only into an empty collection.

    Best-effort: any failure is logged and swallowed so a bad/missing deck can
    never block startup.
    """
    try:
        if col.card_count():
            return
        path = _seed_deck_path()
        if not path:
            print(f"MCAT seed: {_DECK_FILENAME} not found; skipping import")
            return

        print(f"MCAT seed: importing {path} into empty collection")
        # Preserve scheduling/revlog so FSRS has history to derive memory state.
        col.import_anki_package(
            ImportAnkiPackageRequest(
                package_path=str(path),
                options=ImportAnkiPackageOptions(
                    with_scheduling=True,
                    merge_notetypes=False,
                ),
            )
        )
        _run_fsrs(col)
        print("MCAT seed: import + FSRS complete")
    except Exception as exc:
        print(f"MCAT seed failed (continuing without seed): {exc}")


def _run_fsrs(col: Collection) -> None:
    """Enable FSRS and recompute memory state for every imported card.

    Mirrors what saving deck options with FSRS on does: reuse the existing
    presets but flip ``fsrs``/``fsrs_reschedule`` so the backend converts each
    card's revlog into an FSRS memory state (and thus a retrievability).
    """
    data = col.decks.get_deck_configs_for_update(DEFAULT_DECK_ID)
    col.decks.update_deck_configs(
        UpdateDeckConfigs(
            target_deck_id=DEFAULT_DECK_ID,
            configs=[c.config for c in data.all_config],
            removed_config_ids=[],
            mode=UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_NORMAL,
            card_state_customizer=data.card_state_customizer,
            limits=data.current_deck.limits,
            new_cards_ignore_review_limit=data.new_cards_ignore_review_limit,
            fsrs=True,
            apply_all_parent_limits=data.apply_all_parent_limits,
            fsrs_reschedule=True,
            fsrs_health_check=False,
        )
    )
