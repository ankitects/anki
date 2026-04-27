// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
// @vitest-environment jsdom

import { afterEach, describe, expect, test } from "vitest";

import { getSelection } from "@tslib/cross-browser";

import useInputHandler from "./input-handler";

function setSelection(element: HTMLElement, offset: number): void {
    const selection = getSelection(element)!;
    const range = new Range();

    range.setStart(element, offset);
    range.collapse(true);

    selection.removeAllRanges();
    selection.addRange(range);
}

describe("input handler", () => {
    afterEach(() => {
        document.body.replaceChildren();
        getSelection(document.body)?.removeAllRanges();
    });

    test("resets caret to the start of an empty editor after delete input", () => {
        const element = document.createElement("div");
        element.innerHTML = "<br>";
        document.body.appendChild(element);

        const [, setupHandler] = useInputHandler();
        const { destroy } = setupHandler(element);

        setSelection(element, 1);
        element.dispatchEvent(
            new InputEvent("input", {
                bubbles: true,
                inputType: "deleteContentBackward",
            }),
        );

        const selection = getSelection(element)!;
        expect(selection.anchorNode).toBe(element);
        expect(selection.anchorOffset).toBe(0);

        destroy();
    });

    test("does not reset caret for insert input", () => {
        const element = document.createElement("div");
        element.innerHTML = "<br>";
        document.body.appendChild(element);

        const [, setupHandler] = useInputHandler();
        const { destroy } = setupHandler(element);

        setSelection(element, 1);
        element.dispatchEvent(
            new InputEvent("input", {
                bubbles: true,
                inputType: "insertText",
                data: "a",
            }),
        );

        const selection = getSelection(element)!;
        expect(selection.anchorNode).toBe(element);
        expect(selection.anchorOffset).toBe(1);

        destroy();
    });
});
