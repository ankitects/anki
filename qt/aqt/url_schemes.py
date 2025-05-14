# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from markdown import markdown

from aqt.qt import QMessageBox, Qt, QUrl
from aqt.utils import ask_user_dialog, getText, openLink, tr


def show_url_schemes_dialog() -> None:
    from aqt import mw

    default = " ".join(mw.pm.allowed_url_schemes())
    schemes, ok = getText(
        prompt=tr.preferences_url_scheme_prompt(),
        title=tr.preferences_url_schemes(),
        default=default,
    )
    if ok:
        mw.pm.set_allowed_url_schemes(schemes.split(" "))
        mw.pm.save()


def is_supported_scheme(url: QUrl) -> bool:
    from aqt import mw

    scheme = url.scheme().lower()
    allowed_schemes = mw.pm.allowed_url_schemes()

    return scheme in allowed_schemes or scheme in ["http", "https"]


def always_allow_scheme(url: QUrl) -> None:
    from aqt import mw

    scheme = url.scheme().lower()
    mw.pm.always_allow_scheme(scheme)


def open_url_if_supported_scheme(url: QUrl) -> None:
    from aqt import mw

    if is_supported_scheme(url):
        openLink(url)
    else:

        def on_button(idx: int) -> None:
            if idx == 0:
                openLink(url)
            elif idx == 1:
                always_allow_scheme(url)
                openLink(url)

        msg = markdown(
            tr.preferences_url_scheme_warning(link=url.toString(), scheme=url.scheme())
        )
        ask_user_dialog(
            msg,
            buttons=[
                tr.preferences_url_scheme_allow_once(),
                tr.preferences_url_scheme_always_allow(),
                (tr.actions_cancel(), QMessageBox.ButtonRole.RejectRole),
            ],
            parent=mw,
            callback=on_button,
            textFormat=Qt.TextFormat.RichText,
            default_button=0,
        )
