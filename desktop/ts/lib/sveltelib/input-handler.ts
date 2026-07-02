// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getRange, getSelection } from "@tslib/cross-browser";
import { on } from "@tslib/events";
import { isArrowDown, isArrowLeft, isArrowRight, isArrowUp } from "@tslib/keys";
import { singleCallback } from "@tslib/typing";

import { HandlerList } from "./handler-list";

const nbsp = "\xa0";

export type SetupInputHandlerAction = (element: HTMLElement) => { destroy(): void };

export interface InputEventParams {
    event: InputEvent;
}

export interface EventParams {
    event: Event;
}

export interface InsertTextParams {
    event: InputEvent;
    text: Text;
}

type SpecialKeyAction =
    | "caretUp"
    | "caretDown"
    | "caretLeft"
    | "caretRight"
    | "enter"
    | "tab";

export interface SpecialKeyParams {
    event: KeyboardEvent;
    action: SpecialKeyAction;
}

export interface InputHandlerAPI {
    readonly beforeInput: HandlerList<InputEventParams>;
    readonly insertText: HandlerList<InsertTextParams>;
    readonly afterInput: HandlerList<EventParams>;
    readonly pointerDown: HandlerList<{ event: PointerEvent }>;
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
    const afterInput = new HandlerList<EventParams>();

    async function onBeforeInput(this: Element, event: InputEvent): Promise<void> {
        const selection = getSelection(this)!;
        const range = getRange(selection);

        await beforeInput.dispatch({ event });

        if (
            !range
            || !event.inputType.startsWith("insert")
            || insertText.length === 0
        ) {
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

        // We emulate the after input event here, because we prevent
        // the default behavior earlier
        await afterInput.dispatch({ event });
    }

    async function onInput(this: Element, event: Event): Promise<void> {
        await afterInput.dispatch({ event });
    }

    const pointerDown = new HandlerList<{ event: PointerEvent }>();

    function clearInsertText(): void {
        insertText.clear();
    }

    function onPointerDown(event: PointerEvent): void {
        pointerDown.dispatch({ event });
        clearInsertText();
    }

    const specialKey = new HandlerList<SpecialKeyParams>();

    async function onKeyDown(this: Element, event: KeyboardEvent): Promise<void> {
        if (isArrowDown(event)) {
            specialKey.dispatch({ event, action: "caretDown" });
        } else if (isArrowUp(event)) {
            specialKey.dispatch({ event, action: "caretUp" });
        } else if (isArrowRight(event)) {
            specialKey.dispatch({ event, action: "caretRight" });
        } else if (isArrowLeft(event)) {
            specialKey.dispatch({ event, action: "caretLeft" });
        } else if (event.key === "Enter") {
            specialKey.dispatch({ event, action: "enter" });
        } else if (event.code === "Tab") {
            specialKey.dispatch({ event, action: "tab" });
        }
    }

    function setupHandler(element: HTMLElement): { destroy(): void } {
        const destroy = singleCallback(
            on(element, "beforeinput", onBeforeInput),
            on(element, "input", onInput),
            on(element, "blur", clearInsertText),
            on(element, "pointerdown", onPointerDown),
            on(element, "keydown", onKeyDown),
            on(document, "selectionchange", clearInsertText),
        );

        return { destroy };
    }

    return [
        {
            beforeInput,
            insertText,
            afterInput,
            specialKey,
            pointerDown,
        },
        setupHandler,
    ];
}

export default useInputHandler;
