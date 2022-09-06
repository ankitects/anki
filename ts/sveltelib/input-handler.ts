// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { isArrowDown, isArrowUp, isArrowRight, isArrowLeft } from "../lib/keys";
import { getRange, getSelection } from "../lib/cross-browser";
import { on } from "../lib/events";
import { singleCallback } from "../lib/typing";
import { HandlerList } from "./handler-list";

const nbsp = "\xa0";

export type SetupInputHandlerAction = (element: HTMLElement) => { destroy(): void };

export interface InputEventParams {
    event: InputEvent;
}

export interface InsertTextParams {
    event: InputEvent;
    text: Text;
}

type SpecialKeyAction = 'caretUp' | 'caretDown' | 'caretLeft' | 'caretRight' | "enter" | "tab";

export interface SpecialKeyParams {
    event: KeyboardEvent;
    action: SpecialKeyAction;
}

export interface InputHandlerAPI {
    readonly beforeInput: HandlerList<InputEventParams>;
    readonly insertText: HandlerList<InsertTextParams>;
    readonly specialKey: HandlerList<SpecialKeyParams>;
}

/**
 * An interface that allows Svelte components to attach event listeners via triggers.
 * They will be attached to the component(s) that install the manager.
 * Prevents that too many event listeners are attached and allows for some
 * coordination between them.
 */
function useInputHandler(): [InputHandlerAPI, SetupInputHandlerAction] {
    const beforeInput = new HandlerList<InputEventParams>();
    const insertText = new HandlerList<InsertTextParams>();

    async function onBeforeInput(this: Element, event: InputEvent): Promise<void> {
        const selection = getSelection(this)!;
        const range = getRange(selection);

        await beforeInput.dispatch({ event });

        if (!range || event.inputType !== "insertText" || insertText.length === 0) {
            return;
        }

        event.preventDefault();

        const content = !event.data || event.data === " " ? nbsp : event.data;
        const text = new Text(content);

        range.deleteContents();
        range.insertNode(text);
        range.selectNode(text);
        range.collapse(false);

        await insertText.dispatch({ event, text });

        range.commonAncestorContainer.normalize();
    }

    function clearInsertText(): void {
        insertText.clear();
    }

    const specialKey = new HandlerList<SpecialKeyParams>();

    async function onKeyDown(this: Element, event: KeyboardEvent): Promise<void> {
        if (isArrowDown(event)) {
            specialKey.dispatch({ event, action: "caretDown" })
        } else if (isArrowUp(event)) {
            specialKey.dispatch({ event, action: "caretUp" })
        } else if (isArrowRight(event)) {
            specialKey.dispatch({ event, action: "caretRight" })
        } else if (isArrowLeft(event)) {
            specialKey.dispatch({ event, action: "caretLeft" })
        } else if (event.code === "Enter" || event.code === "NumpadEnter") {
            specialKey.dispatch({ event, action: "enter" })
        } else if (event.code === "Tab") {
            specialKey.dispatch({ event, action: "tab" })
        }
    }

    function setupHandler(element: HTMLElement): { destroy(): void } {
        const destroy = singleCallback(
            on(element, "beforeinput", onBeforeInput),
            on(element, "blur", clearInsertText),
            on(element, "pointerdown", clearInsertText),
            on(document, "selectionchange", clearInsertText),
            on(element, "keydown", onKeyDown),
        );

        return { destroy };
    }

    return [
        {
            beforeInput,
            insertText,
            specialKey,
        },
        setupHandler,
    ];
}

export default useInputHandler;
