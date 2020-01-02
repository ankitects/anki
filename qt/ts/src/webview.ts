/* Copyright: Ankitects Pty Ltd and contributors
 * License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html */

// prevent backspace key from going back a page
document.addEventListener("keydown", function(evt: KeyboardEvent) {
    if (evt.keyCode !== 8) {
        return;
    }
    let isText = 0;
    const node = evt.target as Element;
    const nn = node.nodeName;
    if (nn === "INPUT" || nn === "TEXTAREA") {
        isText = 1;
    } else if (nn === "DIV" && (node as HTMLDivElement).contentEditable) {
        isText = 1;
    }
    if (!isText) {
        evt.preventDefault();
    }
});
