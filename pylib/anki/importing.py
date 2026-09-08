# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from typing import TYPE_CHECKING, Any

from anki._legacy import DeprecatedNamesMixinForModule
from anki.collection import (
    Collection,
    ImportAnkiPackageOptions,
    ImportAnkiPackageRequest,
)


class _LegacyAnkiPackageImporter:
    def __init__(self, col: Collection, file: str) -> None:
        self.col = col
        self.file = file

    def run(self) -> None:
        self.col.import_anki_package(
            ImportAnkiPackageRequest(
                package_path=self.file,
                options=ImportAnkiPackageOptions(
                    merge_notetypes=True,
                    with_scheduling=True,
                    with_deck_configs=True,
                ),
            )
        )


_deprecated_names = DeprecatedNamesMixinForModule(globals())
_deprecated_names.register_deprecated_aliases(
    AnkiPackageImporter=_LegacyAnkiPackageImporter
)

if not TYPE_CHECKING:

    def __getattr__(name: str) -> Any:
        return _deprecated_names.__getattr__(name)
