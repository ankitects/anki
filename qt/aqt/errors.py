# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import re
import sys
import time
import traceback
from typing import TYPE_CHECKING, TextIO, cast

from markdown import markdown

import aqt
from anki.collection import HelpPage
from anki.errors import BackendError, Interrupted
from anki.utils import is_win
from aqt.addons import AddonManager, AddonMeta
from aqt.qt import *
from aqt.utils import openHelp, showWarning, supportText, tooltip, tr

if TYPE_CHECKING:
    from aqt.main import AnkiQt


def show_exception(*, parent: QWidget, exception: Exception) -> None:
    "Present a caught exception to the user using a pop-up."
    if isinstance(exception, Interrupted):
        # nothing to do
        return
    if isinstance(exception, BackendError):
        if exception.context:
            print(exception.context)
        if exception.backtrace:
            print(exception.backtrace)
        showWarning(str(exception), parent=parent, help=exception.help_page)
    else:
        # if the error is not originating from the backend, dump
        # a traceback to the console to aid in debugging
        traceback.print_exception(
            None, exception, exception.__traceback__, file=sys.stdout
        )
        showWarning(str(exception), parent=parent)


def is_chromium_cert_error(error: str) -> bool:
    """QtWebEngine sometimes spits out 'unknown error' messages to stderr on Windows.

    They appear to be IDS_SETTINGS_CERTIFICATE_MANAGER_UNKNOWN_ERROR in
    chrome/browser/ui/webui/certificates_handler.cc. At a guess, it's the
    NetErrorToString() method.

    The constant appears to get converted to an ID; the resources are found
    in files like this:

    chrome/app/resources/generated_resources_fr-CA.xtb
    2258:<translation id="3380365263193509176">Erreur inconnue</translation>

    List derived with:
    qtwebengine-chromium% rg --no-heading --no-filename --no-line-number \
        3380365263193509176  | perl -pe 's/.*>(.*)<.*/"$1",/' | sort | uniq
        
    This list has been manually updated to add a different Japanese translation, as
    the translations may change in different Chromium releases.

    Judging by error reports, we can't assume the error falls on a separate line:
    https://forums.ankiweb.net/t/topic/22036/
    """
    if not is_win:
        return False
    for msg in (
        "알 수 없는 오류가 발생했습니다.",
        "Bilinmeyen hata",
        "Eroare necunoscută",
        "Erreur inconnue",
        "Erreur inconnue.",
        "Erro descoñecido",
        "Erro desconhecido",
        "Error desconegut",
        "Error desconocido",
        "Errore ezezaguna",
        "Errore sconosciuto",
        "Gabim i panjohur",
        "Hindi kilalang error",
        "Hitilafu isiyojulikana",
        "Iphutha elingaziwa",
        "Ismeretlen hiba",
        "Kesalahan tidak dikenal",
        "Lỗi không xác định",
        "Naməlum xəta",
        "Nepoznata greška",
        "Nepoznata pogreška",
        "Nezināma kļūda",
        "Nežinoma klaida",
        "Neznáma chyba",
        "Neznámá chyba",
        "Neznana napaka",
        "Nieznany błąd",
        "Noma’lum xatolik",
        "Okänt fel",
        "Onbekende fout",
        "Óþekkt villa",
        "Ralat tidak diketahui",
        "Tundmatu viga",
        "Tuntematon virhe",
        "Ukendt fejl",
        "Ukjent feil",
        "Unbekannter Fehler",
        "Unknown error",
        "Άγνωστο σφάλμα",
        "Белгисиз ката",
        "Белгісіз қате",
        "Невідома помилка",
        "Невядомая памылка",
        "Неизвестна грешка",
        "Неизвестная ошибка",
        "Непозната грешка",
        "Үл мэдэгдэх алдаа",
        "უცნობი შეცდომა",
        "Անհայտ սխալ",
        "שגיאה לא ידועה",
        "خطأ غير معروف",
        "خطای ناشناس",
        "نامعلوم خرابی",
        "ያልታወቀ ስህተት",
        "अज्ञात एरर",
        "अज्ञात गड़बड़ी",
        "अज्ञात त्रुटि",
        "অজানা ত্রুটি",
        "অজ্ঞাত আসোঁৱাহ",
        "ਅਗਿਆਤ ਗੜਬੜ",
        "અજ્ઞાત ભૂલ",
        "ଅଜଣା ତୃଟି",
        "அறியப்படாத பிழை",
        "తెలియని ఎర్రర్",
        "ಅಪರಿಚಿತ ದೋಷ",
        "അജ്ഞാതമായ പിശക്",
        "නොදන්නා දෝෂය",
        "ข้อผิดพลาดที่ไม่รู้จัก",
        "ຄວາມຜິດພາດທີ່ບໍ່ຮູ້ຈັກ",
        "မသိရ အမှား",
        "កំហុសឆ្គងមិនស្គាល់",
        "不明なエラー",
        "未知のエラー",
        "未知的錯誤",
        "未知错误",
    ):
        if error.startswith(msg):
            return True
    return False


if not os.environ.get("DEBUG"):

    def excepthook(etype, val, tb) -> None:  # type: ignore
        sys.stderr.write("%s\n" % ("".join(traceback.format_exception(etype, val, tb))))

    sys.excepthook = excepthook

# so we can be non-modal/non-blocking, without Python deallocating the message
# box ahead of time
_mbox: QMessageBox | None = None


class ErrorHandler(QObject):
    "Catch stderr and write into buffer."
    ivl = 100
    fatal_error_encountered = False

    errorTimer = pyqtSignal()

    def __init__(self, mw: AnkiQt) -> None:
        QObject.__init__(self, mw)
        self.mw = mw
        self.timer: QTimer | None = None
        qconnect(self.errorTimer, self._setTimer)
        self.pool = ""
        self._oldstderr = sys.stderr
        sys.stderr = cast(TextIO, self)

    def unload(self) -> None:
        sys.stderr = self._oldstderr
        sys.excepthook = None

    def write(self, data: str) -> None:
        # dump to stdout
        sys.stdout.write(data)
        # save in buffer
        self.pool += data
        # and update timer
        self.setTimer()

    def setTimer(self) -> None:
        # we can't create a timer from a different thread, so we post a
        # message to the object on the main thread
        self.errorTimer.emit()  # type: ignore

    def _setTimer(self) -> None:
        if not self.timer:
            self.timer = QTimer(self.mw)
            qconnect(self.timer.timeout, self.onTimeout)
        self.timer.setInterval(self.ivl)
        self.timer.setSingleShot(True)
        self.timer.start()

    def tempFolderMsg(self) -> str:
        return tr.qt_misc_unable_to_access_anki_media_folder()

    def onTimeout(self) -> None:
        if self.fatal_error_encountered:
            # suppress follow-up errors caused by the poisoned lock
            return
        error = self.pool
        self.pool = ""
        self.mw.progress.clear()
        if "AbortSchemaModification" in error:
            return
        if "DeprecationWarning" in error:
            return
        if "10013" in error:
            showWarning(tr.qt_misc_your_firewall_or_antivirus_program_is())
            return
        if "invalidTempFolder" in error:
            showWarning(self.tempFolderMsg())
            return
        if "Beautiful Soup is not an HTTP client" in error:
            return
        if "database or disk is full" in error or "Errno 28" in error:
            showWarning(tr.qt_misc_your_computers_storage_may_be_full())
            return
        if "disk I/O error" in error:
            showWarning(markdown(tr.errors_accessing_db()))
            return
        if is_chromium_cert_error(error):
            return

        debug_text = supportText() + "\n" + error

        if "PanicException" in error:
            self.fatal_error_encountered = True
            # ensure no collection-related timers like backup fire
            self.mw.col = None
            user_text = "A fatal error occurred, and Anki must close. Please report this message on the forums."
        else:
            user_text = tr.errors_standard_popup2()
            if self.mw.addonManager.dirty:
                user_text += "\n\n" + self._addonText(error)
                debug_text += addon_debug_info()

        def show_troubleshooting():
            openHelp(HelpPage.TROUBLESHOOTING)

        def copy_debug_info():
            QApplication.clipboard().setText(debug_text)
            tooltip(tr.errors_copied_to_clipboard(), parent=_mbox)

        global _mbox
        _mbox = QMessageBox()
        _mbox.setWindowTitle("Anki")
        _mbox.setText(user_text)
        _mbox.setIcon(QMessageBox.Icon.Warning)
        _mbox.setTextFormat(Qt.TextFormat.PlainText)

        troubleshooting = _mbox.addButton(
            tr.errors_troubleshooting_button(), QMessageBox.ButtonRole.ActionRole
        )
        debug_info = _mbox.addButton(
            tr.errors_copy_debug_info_button(), QMessageBox.ButtonRole.ActionRole
        )
        cancel = _mbox.addButton(QMessageBox.StandardButton.Cancel)
        cancel.setText(tr.actions_close())

        troubleshooting.disconnect()
        troubleshooting.clicked.connect(show_troubleshooting)
        debug_info.disconnect()
        debug_info.clicked.connect(copy_debug_info)

        if self.fatal_error_encountered:
            _mbox.exec()
            sys.exit(1)
        else:
            _mbox.show()

    def _addonText(self, error: str) -> str:
        matches = re.findall(r"addons21(/|\\)(.*?)(/|\\)", error)
        if not matches:
            return tr.errors_may_be_addon()
        # reverse to list most likely suspect first, dict to deduplicate:
        addons = [
            aqt.mw.addonManager.addonName(i[1])
            for i in dict.fromkeys(reversed(matches))
        ]
        addons_str = ", ".join(addons)
        return tr.addons_possibly_involved(addons=addons_str)


def addon_fmt(addmgr: AddonManager, addon: AddonMeta) -> str:
    installed = "0"
    if addon.installed_at:
        try:
            installed = time.strftime(
                "%Y-%m-%dT%H:%M", time.localtime(addon.installed_at)
            )
        except (OverflowError, OSError):
            print("invalid timestamp for", addon.provided_name)
    if addon.provided_name:
        name = addon.provided_name
    else:
        name = "''"
    user = addmgr.getConfig(addon.dir_name)
    default = addmgr.addonConfigDefaults(addon.dir_name)
    if user == default:
        modified = "''"
    else:
        modified = "mod"
    return (
        f"{name} ['{addon.dir_name}', {installed}, '{addon.human_version}', {modified}]"
    )


def addon_debug_info() -> str:
    from aqt import mw

    addmgr = mw.addonManager
    active = []
    activeids = []
    inactive = []
    for addon in addmgr.all_addon_meta():
        if addon.enabled:
            active.append(addon_fmt(addmgr, addon))
            if addon.ankiweb_id():
                activeids.append(addon.dir_name)
        else:
            inactive.append(addon_fmt(addmgr, addon))
    newline = "\n"
    info = f"""\
===Add-ons (active)===
(add-on provided name [Add-on folder, installed at, version, is config changed])
{newline.join(sorted(active))}

===IDs of active AnkiWeb add-ons===
{" ".join(activeids)}

===Add-ons (inactive)===
(add-on provided name [Add-on folder, installed at, version, is config changed])
{newline.join(sorted(inactive))}
"""
    return info
