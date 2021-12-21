// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { writable } from "svelte/store";
import type { Writable } from "svelte/store";
import { on } from "../lib/events";
import { nodeIsText } from "../lib/dom";
import { getSelection } from "../lib/cross-browser";

export type OnInsertCallback = ({ node }: { node: Node }) => Promise<void>;

export interface OnNextInsertTrigger {
    add: (callback: OnInsertCallback) => void;
    remove: () => void;
    active: Writable<boolean>;
}

interface InputManager {
    manager(element: HTMLElement): { destroy(): void };
    getTriggerOnNextInsert(): OnNextInsertTrigger;
}

function getInputManager(): InputManager {
    const onInsertText: { callback: OnInsertCallback; remove: () => void }[] = [];

    function cancelInsertText(): void {
        onInsertText.length = 0;
    }

    function cancelIfInsertText(event: KeyboardEvent): void {
        if (event.key.length !== 1) {
            cancelInsertText();
        }
    }

    async function onBeforeInput(event: InputEvent): Promise<void> {
        if (event.inputType === "insertText" && onInsertText.length > 0) {
            const nbsp = " ";
            const textContent = event.data === " " ? nbsp : event.data ?? nbsp;
            const node = new Text(textContent);

            const selection = getSelection(event.target! as Node)!;
            const range = selection.getRangeAt(0);

            range.deleteContents();

            if (nodeIsText(range.startContainer) && range.startOffset === 0) {
                const parent = range.startContainer.parentNode!;
                parent.insertBefore(node, range.startContainer);
            } else if (
                nodeIsText(range.endContainer) &&
                range.endOffset === range.endContainer.length
            ) {
                const parent = range.endContainer.parentNode!;
                parent.insertBefore(node, range.endContainer.nextSibling!);
            } else {
                range.insertNode(node);
            }

            range.selectNode(node);
            range.collapse(false);

            for (const { callback, remove } of onInsertText) {
                await callback({ node });
                remove();
            }

            event.preventDefault();
        }

        cancelInsertText();
    }

    function onInput(this: HTMLElement): void {
        // prevent unwanted <div> from being left behind when clearing the contents
        // of a field with 'Backspace' or 'Delete'
        if (/^\n?$/.test(this.innerText)) {
            const children = this.children;
            if (children.length === 1 && children.item(0) instanceof HTMLDivElement) {
                this.innerHTML = "";
            }
        }
    }

    function manager(element: HTMLElement): { destroy(): void } {
        const removeBeforeInput = on(element, "beforeinput", onBeforeInput);
        const removePointerDown = on(element, "pointerdown", cancelInsertText);
        const removeBlur = on(element, "blur", cancelInsertText);
        const removeKeyDown = on(
            element,
            "keydown",
            cancelIfInsertText as EventListener,
        );
        const removeInput = on(element, "input", onInput);

        return {
            destroy() {
                removeBeforeInput();
                removePointerDown();
                removeBlur();
                removeKeyDown();
                removeInput();
            },
        };
    }

    function getTriggerOnNextInsert(): OnNextInsertTrigger {
        const active = writable(false);
        let index = NaN;

        function remove() {
            if (!Number.isNaN(index)) {
                delete onInsertText[index];
                active.set(false);
                index = NaN;
            }
        }

        function add(callback: OnInsertCallback): void {
            if (Number.isNaN(index)) {
                index = onInsertText.push({ callback, remove });
                active.set(true);
            }
        }

        return {
            add,
            remove,
            active,
        };
    }

    return {
        manager,
        getTriggerOnNextInsert,
    };
}

export default getInputManager;
