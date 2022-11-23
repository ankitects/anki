# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import html
import re
import sys
import traceback
from typing import TYPE_CHECKING, Optional, TextIO, cast

from markdown import markdown

import aqt
from anki.errors import BackendError, Interrupted
from aqt.qt import *
from aqt.utils import showText, showWarning, supportText, tr

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
        "未知的錯誤",
        "未知错误",
    ):
        if error.startswith(msg):
            return True
    return False


if not os.environ.get("DEBUG"):

    def excepthook(etype, val, tb) -> None:  # type: ignore
        sys.stderr.write(
            "Caught exception:\n%s\n"
            % ("".join(traceback.format_exception(etype, val, tb)))
        )

    sys.excepthook = excepthook


class ErrorHandler(QObject):
    "Catch stderr and write into buffer."
    ivl = 100
    fatal_error_encountered = False

    errorTimer = pyqtSignal()

    def __init__(self, mw: AnkiQt) -> None:
        QObject.__init__(self, mw)
        self.mw = mw
        self.timer: Optional[QTimer] = None
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
        error = html.escape(self.pool)
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

        if "PanicException" in error:
            self.fatal_error_encountered = True
            txt = markdown(
                "**A fatal error occurred, and Anki must close. Please report this message on the forums.**"
            )
            error = f"{supportText() + self._addonText(error)}\n{error}"
        elif self.mw.addonManager.dirty:
            # Older translations include a link to the old discussions site; rewrite it to a newer one
            message = tr.errors_addons_active_popup().replace(
                "https://help.ankiweb.net/discussions/add-ons/",
                "https://forums.ankiweb.net/c/add-ons/11",
            )
            txt = markdown(message)
            error = f"{supportText() + self._addonText(error)}\n{error}"
        else:
            txt = markdown(tr.errors_standard_popup())
            error = f"{supportText()}\n{error}"

        # show dialog
        txt = f"{txt}<div style='white-space: pre-wrap'>{error}</div>"
        showText(txt, type="html", copyBtn=True)
        if self.fatal_error_encountered:
            sys.exit(1)

    def _addonText(self, error: str) -> str:
        matches = re.findall(r"addons21/(.*?)/", error)
        if not matches:
            return ""
        # reverse to list most likely suspect first, dict to deduplicate:
        addons = [
            aqt.mw.addonManager.addonName(i) for i in dict.fromkeys(reversed(matches))
        ]
        # highlight importance of first add-on:
        addons[0] = f"<b>{addons[0]}</b>"
        addons_str = ", ".join(addons)
        return f"{tr.addons_possibly_involved(addons=addons_str)}\n"
