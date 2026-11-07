# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from types import SimpleNamespace

import pytest

from aqt.editor_legacy import EditorWebView, is_relative_image_only
from aqt.qt import QImage, QMimeData


@pytest.mark.parametrize(
    "html",
    [
        '<img src="image.png">',
        '<html><body><!--StartFragment--><img src="image.png" alt=""><!--EndFragment--></body></html>',
        '<img src="/attachments/image.png">',
    ],
)
def test_relative_image_only(html: str) -> None:
    assert is_relative_image_only(html)


@pytest.mark.parametrize(
    "html",
    [
        "<b>text</b>",
        '<b>caption</b><img src="image.png">',
        '<img src="one.png"><img src="two.png">',
        '<img src="https://example.com/image.png">',
        '<img src="//example.com/image.png">',
        '<img src="data:image/png;base64,AAAA">',
        "<img>",
        '<img src="">',
    ],
)
def test_not_relative_image_only(html: str) -> None:
    assert not is_relative_image_only(html)


def process(mime: QMimeData) -> tuple[str, bool]:
    web = SimpleNamespace(
        _processUrls=lambda *_args: None,
        _processImage=lambda *_args: "from-clipboard-image",
        _processText=lambda *_args: None,
    )
    return EditorWebView._processMime(web, mime)  # type: ignore[arg-type]


def mime_with(html: str, with_image: bool) -> QMimeData:
    mime = QMimeData()
    mime.setHtml(html)
    if with_image:
        image = QImage(1, 1, QImage.Format.Format_RGB32)
        mime.setImageData(image)
    return mime


def test_clipboard_image_replaces_relative_image_html(qapp) -> None:
    mime = mime_with('<img src="image.png">', with_image=True)
    assert process(mime) == ("from-clipboard-image", True)


def test_relative_image_html_is_kept_without_a_clipboard_image(qapp) -> None:
    mime = mime_with('<img src="image.png">', with_image=False)
    assert process(mime) == ('<img src="image.png">', False)


def test_other_html_is_kept_with_a_clipboard_image(qapp) -> None:
    mime = mime_with("<b>rich</b>", with_image=True)
    assert process(mime) == ("<b>rich</b>", False)
