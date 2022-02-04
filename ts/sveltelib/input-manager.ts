// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Writable } from "svelte/store";
import { writable } from "svelte/store";

import { getSelection } from "../lib/cross-browser";
import { on } from "../lib/events";
import { id } from "../lib/functional";
import { keyboardEventIsPrintableKey } from "../lib/keys";

export type OnInsertCallback = ({
    node,
    event,
}: {
    node: Node;
    event: InputEvent;
}) => Promise<void>;

export type OnInputCallback = ({ event }: { event: InputEvent }) => Promise<void>;

export interface Trigger<C> {
    add(callback: C): void;
    remove(): void;
    active: Writable<boolean>;
}

export type Managed<C> = Pick<Trigger<C>, "remove"> & { callback: C };

export type InputManagerAction = (element: HTMLElement) => { destroy(): void };

interface InputManager {
    manager: InputManagerAction;
    getTriggerOnNextInsert(): Trigger<OnInsertCallback>;
    getTriggerOnInput(): Trigger<OnInputCallback>;
    getTriggerAfterInput(): Trigger<OnInputCallback>;
}

function trigger<C>(list: Managed<C>[]) {
    return function getTrigger(): Trigger<C> {
        const index = list.length++;
        const active = writable(false);

        function remove() {
            delete list[index];
            active.set(false);
        }

        function add(callback: C): void {
            list[index] = { callback, remove };
            active.set(true);
        }

        return {
            add,
            remove,
            active,
        };
    };
}

const nbsp = "\xa0";

/**
 * An interface that allows Svelte components to attach event listeners via triggers.
 * They will be attached to the component(s) that install the manager.
 * Prevents that too many event listeners are attached and allows for some
 * coordination between them.
 */
function getInputManager(): InputManager {
    const beforeInput: Managed<OnInputCallback>[] = [];
    const beforeInsertText: Managed<OnInsertCallback>[] = [];

    async function onBeforeInput(event: InputEvent): Promise<void> {
        const selection = getSelection(event.target! as Node)!;
        const range = selection.getRangeAt(0);

        for (const { callback } of beforeInput.filter(id)) {
            await callback({ event });
        }

        const filteredBeforeInsertText = beforeInsertText.filter(id);

        if (event.inputType === "insertText" && filteredBeforeInsertText.length > 0) {
            event.preventDefault();
            const textContent = event.data === " " ? nbsp : event.data ?? nbsp;
            const node = new Text(textContent);

            range.deleteContents();
            range.insertNode(node);
            range.selectNode(node);
            range.collapse(false);

            for (const { callback, remove } of filteredBeforeInsertText) {
                await callback({ node, event });
                remove();
            }

            /* we call explicitly because we prevented default */
            callAfterInputHooks(event);
        }
    }

    const afterInput: Managed<OnInputCallback>[] = [];

    async function callAfterInputHooks(event: InputEvent): Promise<void> {
        for (const { callback } of afterInput.filter(id)) {
            await callback({ event });
        }
    }

    function clearInsertText(): void {
        for (const { remove } of beforeInsertText.filter(id)) {
            remove();
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

    function manager(element: HTMLElement): { destroy(): void } {
        const removeBeforeInput = on(element, "beforeinput", onBeforeInput);
        const removeInput = on(
            element,
            "input",
            onInput as unknown as (event: Event) => void,
        );

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

    return {
        manager,
        getTriggerOnNextInsert: trigger(beforeInsertText),
        getTriggerOnInput: trigger(beforeInput),
        getTriggerAfterInput: trigger(afterInput),
    };
}

export default getInputManager;
