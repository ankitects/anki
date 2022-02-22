// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getSelection, getRange } from "../lib/cross-browser";
import { on } from "../lib/events";
import { keyboardEventIsPrintableKey } from "../lib/keys";
import { Handlers } from "./handler-list";

const nbsp = "\xa0";

export type SetupInputManagerAction = (element: HTMLElement) => { destroy(): void };

interface InputManagerAPI {
    readonly beforeInput: Handlers<{ event: InputEvent }, Promise<void>>;
    readonly insertText: Handlers<{ event: InputEvent; node: Node }, Promise<void>>;
    readonly input: Handlers<{ event: InputEvent }, Promise<void>>;
}

/**
 * An interface that allows Svelte components to attach event listeners via triggers.
 * They will be attached to the component(s) that install the manager.
 * Prevents that too many event listeners are attached and allows for some
 * coordination between them.
 */
function getInputManager(): [SetupInputManagerAction, InputManagerAPI] {
    const beforeInput = new Handlers<{ event: InputEvent }, Promise<void>>();
    const insertText = new Handlers<{ event: InputEvent; node: Node }, Promise<void>>();

    async function onBeforeInput(this: Element, event: InputEvent): Promise<void> {
        const selection = getSelection(this)!;
        const range = getRange(selection);

        for (const callback of beforeInput) {
            await callback({ event });
        }

        if (!range || event.inputType !== "insertText" || insertText.length === 0) {
            return;
        }

        event.preventDefault();

        const content = !event.data || event.data === " " ? nbsp : event.data;
        const node = new Text(content);

        range.deleteContents();
        range.insertNode(node);
        range.selectNode(node);
        range.collapse(false);

        for (const callback of insertText) {
            await callback({ node, event });
        }
    }

    function clearInsertText(): void {
        for (const { clear } of insertText) {
            clear();
        }
    }

    function clearInsertTextIfUnprintableKey(event: KeyboardEvent): void {
        /* using arrow keys should cancel */
        if (!keyboardEventIsPrintableKey(event)) {
            clearInsertText();
        }
    }

    function onInput(event: Event): void {
        if (
            !(event instanceof InputEvent) ||
            !(event.currentTarget instanceof HTMLElement)
        ) {
            return;
        }

        // prevent unwanted <div> from being left behind when clearing field contents
        if (
            (event.data === null || event.data === "") &&
            event.currentTarget.children.length === 1 &&
            event.currentTarget.children.item(0) instanceof HTMLDivElement &&
            /^\n?$/.test(event.currentTarget.innerText)
        ) {
            event.currentTarget.innerHTML = "";
        }
    }

    function setupManager(element: HTMLElement): { destroy(): void } {
        const removeBeforeInput = on(element, "beforeinput", onBeforeInput);
        const removeInput = on(element, "input", onInput);

        const removeBlur = on(element, "blur", clearInsertText);
        const removePointerDown = on(element, "pointerdown", clearInsertText);
        const removeKeyDown = on(element, "keydown", clearInsertTextIfUnprintableKey);

        return {
            destroy() {
                removeInput();
                removeBeforeInput();
                removeBlur();
                removePointerDown();
                removeKeyDown();
                removeInput();
            },
        };
    }

    return [
        {
            beforeInsertText,
            beforeInput,
            afterInput,
        },
        setupManager,
    ];
}

export default getInputManager;
