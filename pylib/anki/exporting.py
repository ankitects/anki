# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from typing import TYPE_CHECKING, Any

from anki._legacy import DeprecatedNamesMixinForModule
from anki.collection import Collection, DeckIdLimit, ExportAnkiPackageOptions
from anki.decks import DeckId


class _LegacyAnkiPackageExporter:
    includeSched: bool = False

    def __init__(self, col: Collection):
        self.col = col
        self.did: DeckId | None = None

    def exportInto(self, path: str) -> None:
        self.col.export_anki_package(
            out_path=path,
            options=ExportAnkiPackageOptions(
                with_scheduling=self.includeSched,
                with_deck_configs=self.includeSched,
                with_media=True,
                legacy=True,
            ),
            limit=DeckIdLimit(deck_id=self.did),
        )


_deprecated_names = DeprecatedNamesMixinForModule(globals())
_deprecated_names.register_deprecated_aliases(
    AnkiPackageExporter=_LegacyAnkiPackageExporter
)


if not TYPE_CHECKING:

    def __getattr__(name: str) -> Any:
        return _deprecated_names.__getattr__(name)
