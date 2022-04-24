// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getRange, getSelection } from "../lib/cross-browser";
import { on } from "../lib/events";
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

export interface InputHandlerAPI {
    readonly beforeInput: HandlerList<InputEventParams>;
    readonly insertText: HandlerList<InsertTextParams>;
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

    function setupHandler(element: HTMLElement): { destroy(): void } {
        const beforeInputOff = on(element, "beforeinput", onBeforeInput);

        const blurOff = on(element, "blur", clearInsertText);
        const pointerDownOff = on(element, "pointerdown", clearInsertText);
        const selectionChangeOff = on(document, "selectionchange", clearInsertText);

        return {
            destroy() {
                beforeInputOff();
                blurOff();
                pointerDownOff();
                selectionChangeOff();
            },
        };
    }

    return [
        {
            beforeInput,
            insertText,
        },
        setupHandler,
    ];
}

export default useInputHandler;
