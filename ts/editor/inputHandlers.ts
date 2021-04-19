// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { updateActiveButtons } from "editor-toolbar";
import { EditingArea } from "./editingArea";
import { caretToEnd, nodeIsElement } from "./helpers";
import { triggerChangeTimer } from "./changeTimer";

const getAnchorParent = <T extends Element>(
    predicate: (element: Element) => element is T
) => (currentField: EditingArea): T | null => {
    const anchor = currentField.getSelection()?.anchorNode;

    if (!anchor) {
        return null;
    }

    let anchorParent: T | null = null;
    let element = nodeIsElement(anchor) ? anchor : anchor.parentElement;

    while (element) {
        anchorParent = anchorParent || (predicate(element) ? element : null);
        element = element.parentElement;
    }

    return anchorParent;
};

const getListItem = getAnchorParent(
    (element: Element): element is HTMLLIElement =>
        window.getComputedStyle(element).display === "list-item"
);

const getParagraph = getAnchorParent(
    (element: Element): element is HTMLParamElement => element.tagName === "P"
);

export function onInput(event: Event): void {
    // make sure IME changes get saved
    triggerChangeTimer(event.currentTarget as EditingArea);
    updateActiveButtons();
}

export function onKey(evt: KeyboardEvent): void {
    const currentField = evt.currentTarget as EditingArea;

    // esc clears focus, allowing dialog to close
    if (evt.code === "Escape") {
        currentField.blurEditable();
        return;
    }

    // prefer <br> instead of <div></div>
    if (
        evt.code === "Enter" &&
        !getListItem(currentField) &&
        !getParagraph(currentField)
    ) {
        evt.preventDefault();
        document.execCommand("insertLineBreak");
    }

    // // fix Ctrl+right/left handling in RTL fields
    if (currentField.isRightToLeft()) {
        const selection = currentField.getSelection();
        const granularity = evt.ctrlKey ? "word" : "character";
        const alter = evt.shiftKey ? "extend" : "move";

        switch (evt.code) {
            case "ArrowRight":
                selection.modify(alter, "right", granularity);
                evt.preventDefault();
                return;
            case "ArrowLeft":
                selection.modify(alter, "left", granularity);
                evt.preventDefault();
                return;
        }
    }

    triggerChangeTimer(currentField);
}

globalThis.addEventListener("keydown", (evt: KeyboardEvent) => {
    if (evt.code === "Tab") {
        globalThis.addEventListener(
            "focusin",
            (evt: FocusEvent) => {
                const newFocusTarget = evt.target;
                if (newFocusTarget instanceof EditingArea) {
                    caretToEnd(newFocusTarget);
                    updateActiveButtons();
                }
            },
            { once: true }
        );
    }
});

export function onKeyUp(evt: KeyboardEvent): void {
    const currentField = evt.currentTarget as EditingArea;

    // Avoid div element on remove
    if (evt.code === "Enter" || evt.code === "Backspace") {
        const anchor = currentField.getSelection().anchorNode as Node;

        if (
            nodeIsElement(anchor) &&
            anchor.tagName === "DIV" &&
            !(anchor instanceof EditingArea) &&
            anchor.childElementCount === 1 &&
            anchor.children[0].tagName === "BR"
        ) {
            anchor.replaceWith(anchor.children[0]);
        }
    }
}
