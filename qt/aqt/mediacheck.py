# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import time
from concurrent.futures import Future
from typing import Optional

from send2trash import send2trash

import aqt
from anki import hooks
from anki.lang import _, ngettext
from anki.rsbackend import Interrupted, MediaCheckOutput, Progress, ProgressKind
from aqt.qt import *
from aqt.utils import askUser, restoreGeom, saveGeom, showText, tooltip


def check_media_db(mw: aqt.AnkiQt) -> None:
    c = MediaChecker(mw)
    c.check()


class MediaChecker:
    progress_dialog: Optional[aqt.progress.ProgressDialog]

    def __init__(self, mw: aqt.AnkiQt) -> None:
        self.mw = mw

    def check(self) -> None:
        self.progress_dialog = self.mw.progress.start()
        hooks.bg_thread_progress_callback.append(self._on_progress)
        self.mw.col.close()
        self.mw.taskman.run_in_background(self._check, self._on_finished)

    def _on_progress(self, proceed: bool, progress: Progress) -> bool:
        if progress.kind != ProgressKind.MediaCheck:
            return proceed

        if self.progress_dialog.wantCancel:
            return False

        self.mw.taskman.run_on_main(
            lambda: self.mw.progress.update(_("Checked {}...").format(progress.val))
        )
        return True

    def _check(self) -> MediaCheckOutput:
        "Run the check on a background thread."
        return self.mw.col.media.check()

    def _on_finished(self, future: Future):
        hooks.bg_thread_progress_callback.remove(self._on_progress)
        self.mw.progress.finish()
        self.progress_dialog = None
        self.mw.col.reopen()

        exc = future.exception()
        if isinstance(exc, Interrupted):
            return

        output = future.result()
        report = describe_output(output)

        # show report and offer to delete
        diag = QDialog(self.mw)
        diag.setWindowTitle("Anki")
        layout = QVBoxLayout(diag)
        diag.setLayout(layout)
        text = QTextEdit()
        text.setReadOnly(True)
        text.setPlainText(report)
        layout.addWidget(text)
        box = QDialogButtonBox(QDialogButtonBox.Close)
        layout.addWidget(box)
        if output.unused:
            b = QPushButton(_("Delete Unused Files"))
            b.setAutoDefault(False)
            box.addButton(b, QDialogButtonBox.ActionRole)
            b.clicked.connect(lambda c, u=output.unused, d=diag: deleteUnused(self.mw, u, d))  # type: ignore

        if output.missing:
            if any(map(lambda x: x.startswith("latex-"), output.missing)):
                b = QPushButton(_("Render LaTeX"))
                b.setAutoDefault(False)
                box.addButton(b, QDialogButtonBox.RejectRole)
                b.clicked.connect(self._on_render_latex)  # type: ignore

        box.rejected.connect(diag.reject)  # type: ignore
        diag.setMinimumHeight(400)
        diag.setMinimumWidth(500)
        restoreGeom(diag, "checkmediadb")
        diag.exec_()
        saveGeom(diag, "checkmediadb")

    def _on_render_latex(self):
        self.progress_dialog = self.mw.progress.start()
        try:
            out = self.mw.col.media.render_all_latex(self._on_render_latex_progress)
        finally:
            self.mw.progress.finish()

        if out is not None:
            nid, err = out
            browser = aqt.dialogs.open("Browser", self.mw)
            browser.form.searchEdit.lineEdit().setText("nid:%d" % nid)
            browser.onSearchActivated()
            showText(err, type="html")
        else:
            tooltip(_("All LaTeX rendered."))

    def _on_render_latex_progress(self, count: int) -> bool:
        if self.progress_dialog.wantCancel:
            return False

        self.mw.progress.update(_("Checked {}...").format(count))
        return True


def describe_output(output: MediaCheckOutput) -> str:
    buf = []

    buf.append(_("Missing files: {}").format(len(output.missing)))
    buf.append(_("Unused files: {}").format(len(output.unused)))
    if output.renamed:
        buf.append(_("Renamed files: {}").format(len(output.renamed)))
    if output.oversize:
        buf.append(_("Over 100MB: {}".format(output.oversize)))
    if output.dirs:
        buf.append(_("Subfolders: {}".format(output.dirs)))

    buf.append("")

    if output.renamed:
        buf.append(_("Some files have been renamed for compatibility:"))
        buf.extend(
            _("Renamed: %(old)s -> %(new)s") % dict(old=k, new=v)
            for (k, v) in sorted(output.renamed.items())
        )
        buf.append("")

    if output.oversize:
        buf.append(_("Files over 100MB can not be synced with AnkiWeb."))
        buf.extend(_("Over 100MB: {}").format(f) for f in sorted(output.oversize))
        buf.append("")

    if output.dirs:
        buf.append(_("Folders inside the media folder are not supported."))
        buf.extend(_("Folder: {}").format(f) for f in sorted(output.dirs))
        buf.append("")

    if output.missing:
        buf.append(
            _(
                "The following files are referenced by cards, but were not found in the media folder:"
            )
        )
        buf.extend(_("Missing: {}").format(f) for f in sorted(output.missing))
        buf.append("")

    if output.unused:
        buf.append(
            _(
                "The following files were found in the media folder, but do not appear to be used on any cards:"
            )
        )
        buf.extend(_("Unused: {}").format(f) for f in sorted(output.unused))
        buf.append("")

    return "\n".join(buf)


def deleteUnused(self, unused, diag):
    if not askUser(_("Delete unused media?")):
        return

    mdir = self.col.media.dir()
    self.progress.start(immediate=True)
    try:
        lastProgress = 0
        for c, f in enumerate(unused):
            path = os.path.join(mdir, f)
            if os.path.exists(path):
                send2trash(path)

            now = time.time()
            if now - lastProgress >= 0.3:
                numberOfRemainingFilesToBeDeleted = len(unused) - c
                lastProgress = now
                label = (
                    ngettext(
                        "%d file remaining...",
                        "%d files remaining...",
                        numberOfRemainingFilesToBeDeleted,
                    )
                    % numberOfRemainingFilesToBeDeleted
                )
                self.progress.update(label)
    finally:
        self.progress.finish()
    # caller must not pass in empty list
    # pylint: disable=undefined-loop-variable
    numberOfFilesDeleted = c + 1
    tooltip(
        ngettext("Deleted %d file.", "Deleted %d files.", numberOfFilesDeleted)
        % numberOfFilesDeleted
    )
    diag.close()
