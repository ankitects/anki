# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from markdown import markdown

from aqt.qt import Qt, QUrl
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


def open_url_if_supported_scheme(url: QUrl) -> None:
    from aqt import mw

    if is_supported_scheme(url):
        openLink(url)
    else:

        def on_button(idx: int) -> None:
            if idx == 0:
                show_url_schemes_dialog()

        msg = markdown(
            tr.preferences_url_scheme_warning(link=url.toString(), scheme=url.scheme())
        )
        ask_user_dialog(
            msg,
            buttons=[
                tr.actions_with_ellipsis(action=tr.preferences_url_schemes()),
                tr.actions_close(),
            ],
            parent=mw,
            callback=on_button,
            textFormat=Qt.TextFormat.RichText,
        )
