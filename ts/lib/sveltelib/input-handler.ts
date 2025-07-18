// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getRange, getSelection } from "@tslib/cross-browser";
import { on } from "@tslib/events";
import { isArrowDown, isArrowLeft, isArrowRight, isArrowUp } from "@tslib/keys";
import { singleCallback } from "@tslib/typing";

import { HandlerList } from "./handler-list";
import { UndoManager } from "./undo-manager";

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

export function getMaxOffset(node: Node) {
    if (node.nodeType === Node.TEXT_NODE) {
        if(!node.textContent) return 0;
        return node.textContent.length;
    } else if (node.nodeType === Node.ELEMENT_NODE) {
        return node.childNodes.length;
    }
    return 0;
}


function getCaretPosition(element: Element){
    const selection = getSelection(element)!;
    const range = getRange(selection);
    if(!range) return 0;

    let startNode = range.startContainer;
    let startOffset = range.startOffset;

    if(!range.collapsed){
        if(selection.anchorNode) startNode = selection.anchorNode;
        startOffset = selection.anchorOffset;
    }

    if(startNode.nodeType == Node.TEXT_NODE){
        let counter = 0;
        for(const node of element.childNodes){
            if(node === startNode) break;
            if(node.textContent && node.nodeType == Node.TEXT_NODE) counter += node.textContent.length;
            if(node.nodeName === "BR") counter++;
        }
        counter += startOffset;
        return counter;
    } else {
        let counter = 0;
        for(let i = 0; (i < startOffset) && (i < element.childNodes.length); i++){
            const node = element.childNodes[i];
            if(node.textContent && node.nodeType == Node.TEXT_NODE) counter += node.textContent.length;
            if(node.nodeName === "BR") counter++;
        }
        return counter;
    }
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

    const undoManager = new UndoManager();
    let hasSetupObserver = false;
    const config = {
        attributes: true,
        childList: true,
        subtree: true
    };
    const observer = new MutationObserver(onMutation);

    function onMutation(mutationsList: MutationRecord[], observer){
        const element = <Element>mutationsList[0].target;
        undoManager.register(element.innerHTML, getMaxOffset(element));
    }

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
        if(!hasSetupObserver) {
            observer.observe(this, config);
            hasSetupObserver = true;
        }
        const position = getCaretPosition(this);
        undoManager.register(this.innerHTML, position-1);
        undoManager.clearRedoStack();
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
        else if((event.ctrlKey || event.metaKey) && event.key == "z"){
            event.preventDefault();
            undoManager.undo(this);
        }
        else if((event.ctrlKey || event.metaKey) && event.key == "y"){
            event.preventDefault();
            undoManager.redo(this);
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
