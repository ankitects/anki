# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import json
import re
from collections.abc import Callable
from concurrent.futures import Future
from typing import Any, Match, cast

import aqt
import aqt.forms
import aqt.operations
from anki import stdmodels
from anki.collection import OpChanges
from anki.consts import *
from anki.lang import with_collapsed_whitespace, without_unicode_isolation
from anki.notes import Note
from anki.notetypes_pb2 import StockNotetype
from aqt import AnkiQt, gui_hooks
from aqt.forms import browserdisp
from aqt.operations.notetype import restore_notetype_to_stock, update_notetype_legacy
from aqt.qt import *
from aqt.schema_change_tracker import ChangeTracker
from aqt.sound import av_player, play_clicked_audio
from aqt.theme import theme_manager
from aqt.utils import (
    HelpPage,
    ask_user_dialog,
    askUser,
    disable_help_button,
    downArrow,
    getOnlyText,
    openHelp,
    restoreGeom,
    restoreSplitter,
    saveGeom,
    saveSplitter,
    shortcut,
    showInfo,
    tooltip,
    tr,
)
from aqt.webview import AnkiWebView, AnkiWebViewKind


class CardLayout(QDialog):
    def __init__(
        self,
        mw: AnkiQt,
        note: Note,
        ord: int = 0,
        parent: QWidget | None = None,
        fill_empty: bool = False,
    ) -> None:
        QDialog.__init__(self, parent or mw, Qt.WindowType.Window)
        mw.garbage_collect_on_dialog_finish(self)
        self.mw = aqt.mw
        self.note = note
        self.ord = ord
        self.col = self.mw.col.weakref()
        self.mm = self.mw.col.models
        self.model = note.note_type()
        self.templates = self.model["tmpls"]
        self.fill_empty_action_toggled = fill_empty
        self.night_mode_is_enabled = theme_manager.night_mode
        self.mobile_emulation_enabled = False
        self.have_autoplayed = False
        self.mm._remove_from_cache(self.model["id"])
        self.change_tracker = ChangeTracker(self.mw)
        self.setupTopArea()
        self.setupMainArea()
        self.setupButtons()
        self.setupShortcuts()
        self.setWindowTitle(
            without_unicode_isolation(
                tr.card_templates_card_types_for(val=self.model["name"])
            )
        )
        disable_help_button(self)
        v1 = QVBoxLayout()
        v1.addWidget(self.topArea)
        v1.addWidget(self.mainArea)
        v1.addLayout(self.buttons)
        v1.setContentsMargins(12, 12, 12, 12)
        self.setLayout(v1)
        gui_hooks.card_layout_will_show(self)
        self.redraw_everything()
        restoreGeom(self, "CardLayout")
        restoreSplitter(self.mainArea, "CardLayoutMainArea")
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        self.show()
        # take the focus away from the first input area when starting up,
        # as users tend to accidentally type into the template
        self.setFocus()

    def redraw_everything(self) -> None:
        self.ignore_change_signals = True
        self.updateTopArea()
        self.ignore_change_signals = False
        self.update_current_ordinal_and_redraw(self.ord)

    def update_current_ordinal_and_redraw(self, idx: int) -> None:
        if self.ignore_change_signals:
            return
        self.ord = idx
        self.have_autoplayed = False
        self.fill_fields_from_template()
        self.renderPreview()

    def _isCloze(self) -> bool:
        return self.model["type"] == MODEL_CLOZE

    # Top area
    ##########################################################################

    def setupTopArea(self) -> None:
        self.topArea = QWidget()
        self.topArea.setSizePolicy(
            QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Minimum
        )
        self.topAreaForm = aqt.forms.clayout_top.Ui_Form()
        self.topAreaForm.setupUi(self.topArea)
        self.topAreaForm.templateOptions.setText(
            f"{tr.actions_options()} {downArrow()}"
        )
        qconnect(self.topAreaForm.templateOptions.clicked, self.onMore)
        qconnect(
            self.topAreaForm.templatesBox.currentIndexChanged,
            self.update_current_ordinal_and_redraw,
        )
        self.topAreaForm.card_type_label.setText(tr.card_templates_card_type())

    def updateTopArea(self) -> None:
        self.updateCardNames()

    def updateCardNames(self) -> None:
        self.ignore_change_signals = True
        combo = self.topAreaForm.templatesBox
        combo.clear()
        combo.addItems(
            self._summarizedName(idx, tmpl) for (idx, tmpl) in enumerate(self.templates)
        )
        combo.setCurrentIndex(self.ord)
        combo.setEnabled(not self._isCloze())
        self.ignore_change_signals = False

    def _summarizedName(self, idx: int, tmpl: dict) -> str:
        return "{}: {}: {} -> {}".format(
            idx + 1,
            tmpl["name"],
            self._fieldsOnTemplate(tmpl["qfmt"]),
            self._fieldsOnTemplate(tmpl["afmt"]),
        )

    def _fieldsOnTemplate(self, fmt: str) -> str:
        matches = re.findall("{{[^#/}]+?}}", fmt)
        chars_allowed = 30
        field_names: list[str] = []
        for m in matches:
            # strip off mustache
            m = re.sub(r"[{}]", "", m)
            # strip off modifiers
            m = m.split(":")[-1]
            # don't show 'FrontSide'
            if m == "FrontSide":
                continue

            field_names.append(m)
            chars_allowed -= len(m)
            if chars_allowed <= 0:
                break

        s = "+".join(field_names)
        if chars_allowed <= 0:
            s += "+..."
        return s

    def setupShortcuts(self) -> None:
        self.tform.front_button.setToolTip(shortcut("Ctrl+1"))
        self.tform.back_button.setToolTip(shortcut("Ctrl+2"))
        self.tform.style_button.setToolTip(shortcut("Ctrl+3"))
        QShortcut(  # type: ignore
            QKeySequence("Ctrl+1"),
            self,
            activated=self.tform.front_button.click,
        )
        QShortcut(  # type: ignore
            QKeySequence("Ctrl+2"),
            self,
            activated=self.tform.back_button.click,
        )
        QShortcut(  # type: ignore
            QKeySequence("Ctrl+3"),
            self,
            activated=self.tform.style_button.click,
        )
        QShortcut(  # type: ignore
            QKeySequence("F3"),
            self,
            activated=lambda: (
                self.update_current_ordinal_and_redraw(self.ord - 1)
                if self.ord - 1 > -1
                else None
            ),
        )
        QShortcut(  # type: ignore
            QKeySequence("F4"),
            self,
            activated=lambda: (
                self.update_current_ordinal_and_redraw(self.ord + 1)
                if self.ord + 1 < len(self.templates)
                else None
            ),
        )
        for i in range(min(len(self.cloze_numbers), 9)):
            QShortcut(  # type: ignore
                QKeySequence(f"Alt+{i+1}"),
                self,
                activated=lambda n=i: self.pform.cloze_number_combo.setCurrentIndex(n),
            )

    # Main area setup
    ##########################################################################

    def setupMainArea(self) -> None:
        split = self.mainArea = QSplitter()
        split.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        split.setOrientation(Qt.Orientation.Horizontal)
        left = QWidget()
        tform = self.tform = aqt.forms.template.Ui_Form()
        tform.setupUi(left)
        self.setup_edit_area()
        split.addWidget(left)
        split.setCollapsible(0, False)

        right = QWidget()
        self.pform = aqt.forms.preview.Ui_Form()
        pform = self.pform
        pform.setupUi(right)
        pform.preview_front.setText(tr.card_templates_front_preview())
        pform.preview_back.setText(tr.card_templates_back_preview())
        pform.preview_box.setTitle(tr.card_templates_preview_box())

        self.setup_preview()
        split.addWidget(right)
        split.setCollapsible(1, False)

    def setup_edit_area(self) -> None:
        tform = self.tform
        editor = tform.edit_area

        tform.front_button.setText(tr.card_templates_front_template())
        tform.back_button.setText(tr.card_templates_back_template())
        tform.style_button.setText(tr.card_templates_template_styling())
        tform.template_box.setTitle(tr.card_templates_template_box())

        cnt = self.mw.col.models.use_count(self.model)
        tform.changes_affect_label.setText(
            self.col.tr.card_templates_changes_will_affect_notes(count=cnt)
        )

        qconnect(editor.textChanged, self.write_edits_to_template_and_redraw)
        qconnect(tform.front_button.clicked, self.on_editor_toggled)
        qconnect(tform.back_button.clicked, self.on_editor_toggled)
        qconnect(tform.style_button.clicked, self.on_editor_toggled)

        self.current_editor_index = 0
        editor.setAcceptRichText(False)
        font = QFont("Consolas")
        if not font.exactMatch():
            font = QFontDatabase.systemFont(QFontDatabase.SystemFont.FixedFont)
        editor.setFont(font)
        tab_width = self.fontMetrics().horizontalAdvance(" " * 4)
        editor.setTabStopDistance(tab_width)

        palette = editor.palette()
        palette.setColor(
            QPalette.ColorGroup.Inactive,
            QPalette.ColorRole.Highlight,
            QColor("#4169e1" if theme_manager.night_mode else "#FFFF80"),
        )
        palette.setColor(
            QPalette.ColorGroup.Inactive,
            QPalette.ColorRole.HighlightedText,
            QColor("#ffffff" if theme_manager.night_mode else "#000000"),
        )
        editor.setPalette(palette)

        widg = tform.search_edit
        widg.setPlaceholderText("Search")
        qconnect(widg.textChanged, self.on_search_changed)
        qconnect(widg.returnPressed, self.on_search_next)

    def setup_cloze_number_box(self) -> None:
        names = (tr.card_templates_card(val=n) for n in self.cloze_numbers)
        self.pform.cloze_number_combo.addItems(names)
        try:
            idx = self.cloze_numbers.index(self.ord + 1)
            self.pform.cloze_number_combo.setCurrentIndex(idx)
        except ValueError:
            # invalid cloze
            pass
        qconnect(
            self.pform.cloze_number_combo.currentIndexChanged, self.on_change_cloze
        )

    def on_change_cloze(self, idx: int) -> None:
        self.ord = self.cloze_numbers[idx] - 1
        self.have_autoplayed = False
        self._renderPreview()

    def on_editor_toggled(self) -> None:
        if self.tform.front_button.isChecked():
            self.current_editor_index = 0
            self.pform.preview_front.setChecked(True)
            self.on_preview_toggled()
            self.add_field_button.setHidden(False)
        elif self.tform.back_button.isChecked():
            self.current_editor_index = 1
            self.pform.preview_back.setChecked(True)
            self.on_preview_toggled()
            self.add_field_button.setHidden(False)
        else:
            self.current_editor_index = 2
            self.add_field_button.setHidden(True)

        self.fill_fields_from_template()

    def on_search_changed(self, text: str) -> None:
        editor = self.tform.edit_area
        if not editor.find(text):
            # try again from top
            cursor = editor.textCursor()
            cursor.movePosition(QTextCursor.MoveOperation.Start)
            editor.setTextCursor(cursor)
            if not editor.find(text):
                tooltip("No matches found.")

    def on_search_next(self) -> None:
        text = self.tform.search_edit.text()
        self.on_search_changed(text)

    def setup_preview(self) -> None:
        pform = self.pform
        self.preview_web = AnkiWebView(kind=AnkiWebViewKind.CARD_LAYOUT)
        pform.verticalLayout.addWidget(self.preview_web)
        pform.verticalLayout.setStretch(1, 99)
        pform.preview_front.isChecked()
        qconnect(pform.preview_front.clicked, self.on_preview_toggled)
        qconnect(pform.preview_back.clicked, self.on_preview_toggled)
        pform.preview_settings.setText(
            f"{tr.card_templates_preview_settings()} {downArrow()}"
        )
        qconnect(pform.preview_settings.clicked, self.on_preview_settings)

        self.preview_web.stdHtml(
            self.mw.reviewer.revHtml(),
            css=["css/reviewer.css"],
            js=[
                "js/mathjax.js",
                "js/vendor/mathjax/tex-chtml-full.js",
                "js/reviewer.js",
            ],
            context=self,
        )
        self.preview_web.allow_drops = True
        self.preview_web.eval("_blockDefaultDragDropBehavior();")
        self.preview_web.set_bridge_command(self._on_bridge_cmd, self)

        gui_hooks.card_review_webview_did_init(
            self.preview_web, AnkiWebViewKind.CARD_LAYOUT
        )

        if self._isCloze():
            nums = list(self.note.cloze_numbers_in_fields())
            if self.ord + 1 not in nums:
                # current card is empty
                nums.append(self.ord + 1)
            self.cloze_numbers = sorted(nums)
            self.setup_cloze_number_box()
        else:
            self.cloze_numbers = []
            self.pform.cloze_number_combo.setHidden(True)

    def on_fill_empty_action_toggled(self) -> None:
        self.fill_empty_action_toggled = not self.fill_empty_action_toggled
        self.on_preview_toggled()

    def on_night_mode_action_toggled(self) -> None:
        self.night_mode_is_enabled = not self.night_mode_is_enabled
        force = json.dumps(self.night_mode_is_enabled)
        self.preview_web.eval(
            f"document.documentElement.classList.toggle('night-mode', {force});"
        )
        self.on_preview_toggled()

    def on_mobile_class_action_toggled(self) -> None:
        self.mobile_emulation_enabled = not self.mobile_emulation_enabled
        self.on_preview_toggled()

    def on_preview_settings(self) -> None:
        m = QMenu(self)

        a = m.addAction(tr.card_templates_fill_empty())
        a.setCheckable(True)
        a.setChecked(self.fill_empty_action_toggled)
        qconnect(a.triggered, self.on_fill_empty_action_toggled)
        if not self.note_has_empty_field():
            a.setVisible(False)

        a = m.addAction(tr.card_templates_night_mode())
        a.setCheckable(True)
        a.setChecked(self.night_mode_is_enabled)
        qconnect(a.triggered, self.on_night_mode_action_toggled)

        a = m.addAction(tr.card_templates_add_mobile_class())
        a.setCheckable(True)
        a.setChecked(self.mobile_emulation_enabled)
        qconnect(a.toggled, self.on_mobile_class_action_toggled)

        m.popup(self.pform.preview_settings.mapToGlobal(QPoint(0, 0)))

    def on_preview_toggled(self) -> None:
        self.have_autoplayed = False
        self._renderPreview()

    def _on_bridge_cmd(self, cmd: str) -> Any:
        if cmd.startswith("play:"):
            play_clicked_audio(cmd, self.rendered_card)

    def note_has_empty_field(self) -> bool:
        for field in self.note.fields:
            if not field.strip():
                # ignores HTML, but this should suffice
                return True
        return False

    # Buttons
    ##########################################################################

    def setupButtons(self) -> None:
        l = self.buttons = QHBoxLayout()
        help = QPushButton(tr.actions_help())
        help.setAutoDefault(False)
        l.addWidget(help)
        qconnect(help.clicked, self.onHelp)
        l.addStretch()
        self.add_field_button = QPushButton(tr.fields_add_field())
        self.add_field_button.setAutoDefault(False)
        l.addWidget(self.add_field_button)
        qconnect(self.add_field_button.clicked, self.onAddField)
        if not self._isCloze():
            flip = QPushButton(tr.card_templates_flip())
            flip.setAutoDefault(False)
            l.addWidget(flip)
            qconnect(flip.clicked, self.onFlip)
        l.addStretch()
        save = QPushButton(tr.actions_save())
        save.setAutoDefault(False)
        save.setShortcut(QKeySequence("Ctrl+Return"))
        l.addWidget(save)
        qconnect(save.clicked, self.accept)

        close = QPushButton(tr.actions_cancel())
        close.setAutoDefault(False)
        l.addWidget(close)
        qconnect(close.clicked, self.reject)

    # Reading/writing question/answer/css
    ##########################################################################

    def current_template(self) -> dict:
        if self._isCloze():
            return self.templates[0]
        return self.templates[self.ord]

    def fill_fields_from_template(self) -> None:
        t = self.current_template()
        self.ignore_change_signals = True

        if self.current_editor_index == 0:
            text = t["qfmt"]
        elif self.current_editor_index == 1:
            text = t["afmt"]
        else:
            text = self.model["css"]

        self.tform.edit_area.setPlainText(text)
        self.ignore_change_signals = False

    def write_edits_to_template_and_redraw(self) -> None:
        if self.ignore_change_signals:
            return

        self.change_tracker.mark_basic()

        text = self.tform.edit_area.toPlainText()

        if self.current_editor_index == 0:
            self.current_template()["qfmt"] = text
        elif self.current_editor_index == 1:
            self.current_template()["afmt"] = text
        else:
            self.model["css"] = text

        self.renderPreview()

    # Preview
    ##########################################################################

    _previewTimer: QTimer | None = None

    def renderPreview(self) -> None:
        # schedule a preview when timing stops
        self.cancelPreviewTimer()
        self._previewTimer = self.mw.progress.timer(
            200, self._renderPreview, False, parent=self
        )

    def cancelPreviewTimer(self) -> None:
        if self._previewTimer:
            self._previewTimer.stop()
            self._previewTimer = None

    def _renderPreview(self) -> None:
        self.cancelPreviewTimer()

        c = self.rendered_card = self.note.ephemeral_card(
            self.ord,
            custom_note_type=self.model,
            custom_template=self.current_template(),
            fill_empty=self.fill_empty_action_toggled,
        )

        ti = self.maybeTextInput

        bodyclass = theme_manager.body_classes_for_card_ord(
            c.ord, self.night_mode_is_enabled
        )

        if self.pform.preview_front.isChecked():
            q = ti(self.mw.prepare_card_text_for_display(c.question()))
            q = gui_hooks.card_will_show(q, c, "clayoutQuestion")
            text = q
        else:
            a = ti(self.mw.prepare_card_text_for_display(c.answer()), type="a")
            a = gui_hooks.card_will_show(a, c, "clayoutAnswer")
            text = a

        # use _showAnswer to avoid the longer delay
        self.preview_web.eval(f"_showAnswer({json.dumps(text)},'{bodyclass}');")
        self.preview_web.eval(
            f"_emulateMobile({json.dumps(self.mobile_emulation_enabled)});"
        )

        if not self.have_autoplayed:
            self.have_autoplayed = True

            if c.autoplay():
                self.preview_web.setPlaybackRequiresGesture(False)
                if self.pform.preview_front.isChecked():
                    audio = c.question_av_tags()
                else:
                    audio = c.answer_av_tags()
            else:
                audio = []
                self.preview_web.setPlaybackRequiresGesture(True)
            side = "question" if self.pform.preview_front.isChecked() else "answer"
            gui_hooks.av_player_will_play_tags(
                audio,
                side,
                self,
            )
            av_player.play_tags(audio)

        self.updateCardNames()

    def maybeTextInput(self, txt: str, type: str = "q") -> str:
        if "[[type:" not in txt:
            return txt
        origLen = len(txt)
        txt = txt.replace("<hr id=answer>", "")
        hadHR = origLen != len(txt)

        def answerRepl(match: Match) -> str:
            res = self.mw.col.compare_answer("example", "sample")
            if hadHR:
                res = f"<hr id=answer>{res}"
            return res

        type_filter = r"\[\[type:.+?\]\]"
        repl: str | Callable

        if type == "q":
            repl = "<input id='typeans' type=text value='example' readonly='readonly'>"
            repl = f"<center>{repl}</center>"
        else:
            repl = answerRepl
        out = re.sub(type_filter, repl, txt, count=1)

        warning = f"<center><b>{tr.card_templates_type_boxes_warning()}</b></center>"
        return re.sub(type_filter, warning, out)

    # Card operations
    ######################################################################

    def onRemove(self) -> None:
        if len(self.templates) < 2:
            showInfo(tr.card_templates_at_least_one_card_type_is())
            return

        def get_count() -> int:
            ord = self.current_template()["ord"]
            return self.mm.template_use_count(self.model["id"], ord)

        def on_done(fut: Future) -> None:
            card_cnt = fut.result()

            template = self.current_template()
            cards = tr.card_templates_card_count(count=card_cnt)
            msg = tr.card_templates_delete_the_as_card_type_and(
                template=template["name"],
                # unlike most cases, 'cards' is a string in this message
                cards=cards,  # type: ignore[arg-type]
            )
            if not askUser(msg):
                return

            if not self.change_tracker.mark_schema():
                return

            self.onRemoveInner(template)

        self.mw.taskman.with_progress(get_count, on_done)

    def onRemoveInner(self, template: dict) -> None:
        self.mm.remove_template(self.model, template)

        # ensure current ordinal is within bounds
        idx = self.ord
        if idx >= len(self.templates):
            self.ord = len(self.templates) - 1

        self.redraw_everything()

    def onRename(self) -> None:
        template = self.current_template()
        name = getOnlyText(tr.actions_new_name(), default=template["name"]).replace(
            '"', ""
        )
        if not name.strip():
            return

        template["name"] = name
        self.redraw_everything()

    def onReorder(self) -> None:
        n = len(self.templates)
        template = self.current_template()
        current_pos = self.templates.index(template) + 1
        pos_txt = getOnlyText(
            tr.card_templates_enter_new_card_position_1(val=n),
            default=str(current_pos),
        )
        if not pos_txt:
            return
        try:
            pos = int(pos_txt)
        except ValueError:
            return
        if pos < 1 or pos > n:
            return
        if pos == current_pos:
            return
        new_idx = pos - 1
        if not self.change_tracker.mark_schema():
            return
        self.mm.reposition_template(self.model, template, new_idx)
        self.ord = new_idx
        self.redraw_everything()

    def _newCardName(self) -> str:
        n = len(self.templates) + 1
        while 1:
            name = without_unicode_isolation(tr.card_templates_card(val=n))
            if name not in [t["name"] for t in self.templates]:
                break
            n += 1
        return name

    def onAddCard(self) -> None:
        cnt = self.mw.col.models.use_count(self.model)
        txt = tr.card_templates_this_will_create_card_proceed(count=cnt)
        if cnt and not askUser(txt):
            return
        if not self.change_tracker.mark_schema():
            return
        name = self._newCardName()
        t = self.mm.new_template(name)
        old = self.current_template()
        t["qfmt"] = old["qfmt"]
        t["afmt"] = old["afmt"]
        self.mm.add_template(self.model, t)
        self.ord = len(self.templates) - 1
        self.redraw_everything()

    def on_restore_to_default(
        self, force_kind: StockNotetype.Kind.V | None = None
    ) -> None:
        if force_kind is None and not self.model.get("originalStockKind", 0):
            SelectStockNotetype(
                mw=self.mw,
                on_success=lambda kind: self.on_restore_to_default(force_kind=kind),
                parent=self,
            )
            return

        if not askUser(
            with_collapsed_whitespace(
                tr.card_templates_restore_to_default_confirmation()
            ),
            defaultno=True,
        ):
            return

        def on_success(changes: OpChanges) -> None:
            self.change_tracker.set_unchanged()
            self.close()
            showInfo(tr.card_templates_restored_to_default(), parent=self.mw)

        restore_notetype_to_stock(
            parent=self, notetype_id=self.model["id"], force_kind=force_kind
        ).success(on_success).run_in_background()

    def onFlip(self) -> None:
        old = self.current_template()
        self._flipQA(old, old)
        self.redraw_everything()

    def _flipQA(self, src: dict, dst: dict) -> None:
        m = re.match("(?s)(.+)<hr id=answer>(.+)", src["afmt"])
        if not m:
            showInfo(tr.card_templates_anki_couldnt_find_the_line_between())
            return
        self.change_tracker.mark_basic()
        dst["afmt"] = "{{FrontSide}}\n\n<hr id=answer>\n\n%s" % src["qfmt"]
        dst["qfmt"] = m.group(2).strip()

    def onMore(self) -> None:
        m = QMenu(self)

        a = m.addAction(
            tr.actions_with_ellipsis(action=tr.card_templates_restore_to_default())
        )
        qconnect(
            a.triggered,
            lambda: self.on_restore_to_default(),  # pylint: disable=unnecessary-lambda
        )

        if not self._isCloze():
            a = m.addAction(tr.card_templates_add_card_type())
            qconnect(a.triggered, self.onAddCard)

            a = m.addAction(tr.card_templates_remove_card_type())
            qconnect(a.triggered, self.onRemove)

            a = m.addAction(tr.card_templates_rename_card_type())
            qconnect(a.triggered, self.onRename)

            a = m.addAction(tr.card_templates_reposition_card_type())
            qconnect(a.triggered, self.onReorder)

            m.addSeparator()

            t = self.current_template()
            if t["did"]:
                s = tr.card_templates_on()
            else:
                s = tr.card_templates_off()
            a = m.addAction(tr.card_templates_deck_override() + s)
            qconnect(a.triggered, self.onTargetDeck)

        a = m.addAction(tr.card_templates_browser_appearance())
        qconnect(a.triggered, self.onBrowserDisplay)

        m.popup(self.topAreaForm.templateOptions.mapToGlobal(QPoint(0, 0)))

    def onBrowserDisplay(self) -> None:
        d = QDialog()
        disable_help_button(d)
        f = aqt.forms.browserdisp.Ui_Dialog()
        f.setupUi(d)
        t = self.current_template()
        f.qfmt.setText(t.get("bqfmt", ""))
        f.afmt.setText(t.get("bafmt", ""))
        if t.get("bfont"):
            f.overrideFont.setChecked(True)
        f.font.setCurrentFont(QFont(t.get("bfont") or "Arial"))
        f.fontSize.setValue(t.get("bsize") or 12)
        qconnect(f.buttonBox.accepted, lambda: self.onBrowserDisplayOk(f))
        d.exec()

    def onBrowserDisplayOk(self, f: browserdisp.Ui_Dialog) -> None:
        t = self.current_template()
        self.change_tracker.mark_basic()
        t["bqfmt"] = f.qfmt.text().strip()
        t["bafmt"] = f.afmt.text().strip()
        if f.overrideFont.isChecked():
            t["bfont"] = f.font.currentFont().family()
            t["bsize"] = f.fontSize.value()
        else:
            for key in ("bfont", "bsize"):
                if key in t:
                    del t[key]

    def onTargetDeck(self) -> None:
        from aqt.tagedit import TagEdit

        t = self.current_template()
        d = QDialog(self)
        d.setWindowTitle("Anki")
        disable_help_button(d)
        d.setMinimumWidth(400)
        l = QVBoxLayout()
        lab = QLabel(
            tr.card_templates_enter_deck_to_place_new(val="%s")
            % self.current_template()["name"]
        )
        lab.setWordWrap(True)
        l.addWidget(lab)
        te = TagEdit(d, type=1)
        te.setCol(self.col)
        l.addWidget(te)
        if t["did"]:
            te.setText(self.col.decks.get(t["did"])["name"])
            te.selectAll()
        bb = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
        qconnect(bb.rejected, d.close)
        l.addWidget(bb)
        d.setLayout(l)
        d.exec()
        self.change_tracker.mark_basic()
        if not te.text().strip():
            t["did"] = None
        else:
            t["did"] = self.col.decks.id(te.text())

    def onAddField(self) -> None:
        diag = QDialog(self)
        form = aqt.forms.addfield.Ui_Dialog()
        form.setupUi(diag)
        disable_help_button(diag)
        fields = [f["name"] for f in self.model["flds"]]
        form.fields.addItems(fields)
        form.fields.setCurrentRow(0)
        form.font.setCurrentFont(QFont("Arial"))
        form.size.setValue(20)
        if not diag.exec():
            return
        row = form.fields.currentIndex().row()
        if row >= 0:
            self._addField(
                fields[row],
                form.font.currentFont().family(),
                form.size.value(),
            )

    def _addField(self, field: str, font: str, size: int) -> None:
        text = self.tform.edit_area.toPlainText()
        text += (
            "\n<div style='font-family: \"%s\"; font-size: %spx;'>{{%s}}</div>\n"
            % (
                font,
                size,
                field,
            )
        )
        self.tform.edit_area.setPlainText(text)
        self.change_tracker.mark_basic()
        self.write_edits_to_template_and_redraw()

    # Closing & Help
    ######################################################################

    def accept(self) -> None:
        def on_done(changes: OpChanges) -> None:
            tooltip(tr.card_templates_changes_saved(), parent=self.parentWidget())
            self.cleanup()
            gui_hooks.sidebar_should_refresh_notetypes()
            QDialog.accept(self)

        update_notetype_legacy(parent=self, notetype=self.model).success(
            on_done
        ).run_in_background()

    def reject(self) -> None:
        def _reject() -> None:
            self.cleanup()
            QDialog.reject(self)

        def callback(choice: int) -> None:
            if choice == 0:
                self.accept()
            elif choice == 1:
                _reject()

        if self.change_tracker.changed():
            ask_user_dialog(
                text=tr.card_templates_discard_changes(),
                callback=callback,
                buttons=[
                    QMessageBox.StandardButton.Save,
                    QMessageBox.StandardButton.Discard,
                    QMessageBox.StandardButton.Cancel,
                ],
                default_button=2,
                parent=self,
            )
        else:
            _reject()

    def cleanup(self) -> None:
        self.cancelPreviewTimer()
        av_player.stop_and_clear_queue()
        saveGeom(self, "CardLayout")
        saveSplitter(self.mainArea, "CardLayoutMainArea")
        self.preview_web.cleanup()
        self.preview_web = None
        self.model = None
        self.rendered_card = None
        self.mw = None

    def onHelp(self) -> None:
        openHelp(HelpPage.TEMPLATES)


class SelectStockNotetype(QDialog):
    def __init__(
        self,
        mw: AnkiQt,
        on_success: Callable[[StockNotetype.Kind.V], None],
        parent: QWidget,
    ) -> None:
        self.mw = mw
        QDialog.__init__(self, parent, Qt.WindowType.Window)
        self.dialog = aqt.forms.addmodel.Ui_Dialog()
        self.dialog.setupUi(self)
        self.setWindowTitle("Anki")
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        disable_help_button(self)
        stock_types = stdmodels.get_stock_notetypes(mw.col)

        for name, func in stock_types:
            item = QListWidgetItem(name)
            self.dialog.models.addItem(item)
        self.dialog.models.setCurrentRow(0)
        # the list widget will swallow the enter key
        s = QShortcut(QKeySequence("Return"), self)
        qconnect(s.activated, self.accept)
        # help
        # self.dialog.buttonBox.standardButton(QDialogButtonBox.StandardButton.Help).
        self.on_success = on_success
        self.show()

    def reject(self) -> None:
        QDialog.reject(self)

    def accept(self) -> None:
        kind = cast(StockNotetype.Kind.ValueType, self.dialog.models.currentRow())
        QDialog.accept(self)
        # On Mac, we need to allow time for the existing modal to close or
        # Qt gets confused.
        self.mw.progress.single_shot(100, lambda: self.on_success(kind), True)
