// @vitest-environment jsdom
// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { expect, test, vi } from "vitest";

import { wrapInternal } from "./wrap";

test("wrapInternal preserves HTML and converts <br> to newline inside mathjax", () => {
    const execCommandMock = vi.fn();
    (document as any).execCommand = execCommandMock;

    document.body.innerHTML = "<div id=\"base\"><b>Line1</b><br /><i>Line2</i></div>";
    const base = document.getElementById("base")!;

    const textNode1 = base.querySelector("b")!.firstChild!;
    const textNode2 = base.querySelector("i")!.firstChild!;

    const range = document.createRange();
    range.setStart(textNode1, 0);
    range.setEnd(textNode2, textNode2.textContent!.length);

    const selection = document.getSelection()!;
    selection.removeAllRanges();
    selection.addRange(range);

    wrapInternal(base, "<anki-mathjax focusonmount>", "</anki-mathjax>", false);

    expect(execCommandMock).toHaveBeenCalledWith(
        "inserthtml",
        false,
        "<anki-mathjax focusonmount><b>Line1</b>\n<i>Line2</i></anki-mathjax>",
    );
});
