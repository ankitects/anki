// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import eventStore from "./event-store";
import subscribeToUpdates from "./subscribe-updates";
import type { Writable } from "svelte/store";
import type { Callback } from "../lib/typing";

const store = eventStore(document, "click", MouseEvent);

/**
 * Typically the right-sided mouse button.
 */
function isSecondaryButton(event: MouseEvent): boolean {
    return event.button === 2;
}

interface CloseOnClickProps {
    active: Writable<boolean>;
    /**
     * Clicking on the reference element will not trigger, the reference
     * should trigger itself.
     */
    reference: EventTarget;
    inside: boolean;
    outside: boolean;
}

/**
 * The goal of this action is to turn itself inactive.
 * Once it is active, it will attach event listeners, that listen for a
 * _trigger_ to turn itself off, and remove those event listeners again.
 */
function closeOnClick(
    element: HTMLElement,
    { active, reference, inside = false, outside = true }: CloseOnClickProps,
): { destroy(): void; update(props: CloseOnClickProps): void } {
    let currentReference = reference;

    function trigger(): void {
        active.set(false);
    }

    function isTriggerClick(path: EventTarget[]): boolean {
        return (
            // Reference element was clicked, e.g. the button.
            // The reference element needs to handle opening/closing itself.
            !path.includes(currentReference) &&
            ((inside && path.includes(element)) || (outside && !path.includes(element)))
        );
    }

    function shouldClose(event: MouseEvent): void {
        if (isSecondaryButton(event)) {
            return trigger();
        }

        if (isTriggerClick(event.composedPath())) {
            return trigger();
        }
    }

    let destroy: Callback | null;

    function doDestroy(): void {
        destroy?.();
        destroy = null;
    }

    active.subscribe((value: boolean): void => {
        if (value && !destroy) {
            destroy = subscribeToUpdates(store, shouldClose);
        } else if (!value) {
            doDestroy();
        }
    });

    function update({ reference }: CloseOnClickProps): void {
        currentReference = reference;
    }

    return {
        destroy: doDestroy,
        update,
    };
}

export default closeOnClick;
