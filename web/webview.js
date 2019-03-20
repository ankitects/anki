/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

// prevent backspace key from going back a page
document.addEventListener("keydown", function (evt) {
    if (evt.keyCode !== 8) {
        return;
    }
    var isText = 0;
    var nn = evt.target.nodeName;
    if (nn === "INPUT" || nn === "TEXTAREA") {
        isText = 1;
    } else if (nn === "DIV" && evt.target.contentEditable) {
        isText = 1;
    }
    if (!isText) {
        evt.preventDefault();
    }
});
