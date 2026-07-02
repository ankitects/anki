# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
import aqt.main
from anki.decks import DeckId
from aqt.qt import *
from aqt.utils import disable_help_button, restoreGeom, saveGeom, tr
from aqt.webview import AnkiWebView, AnkiWebViewKind


class ConceptGraphDialog(QDialog):
    """Read-only knowledge map for a deck (its topics/readings and how mastered
    they are). Hosted in an API-enabled webview so it can call the backend."""

    TITLE = "conceptGraph"
    silentlyClose = True

    def __init__(self, mw: aqt.main.AnkiQt, deck_id: DeckId) -> None:
        QDialog.__init__(self, mw)
        self.mw = mw
        self._setup_ui(deck_id)
        self.show()

    def _setup_ui(self, deck_id: DeckId) -> None:
        self.mw.garbage_collect_on_dialog_finish(self)
        disable_help_button(self)
        restoreGeom(self, self.TITLE, default_size=(800, 800))

        self.web = AnkiWebView(kind=AnkiWebViewKind.CONCEPT_GRAPH)
        self.web.load_sveltekit_page(f"concept-graph/{deck_id}")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)

        self.setWindowTitle(tr.statistics_concept_map())

    def reject(self) -> None:
        self.web.cleanup()
        self.web = None  # type: ignore
        saveGeom(self, self.TITLE)
        QDialog.reject(self)


def show_concept_graph(mw: aqt.main.AnkiQt, deck_id: DeckId) -> None:
    ConceptGraphDialog(mw, deck_id)
