// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Readable } from "svelte/store";
import { derived } from "svelte/store";

/**
 * Typically the right-sided mouse button.
 */
function isSecondaryButton(event: MouseEvent): boolean {
    return event.button === 2;
}

interface ClosingClickArgs {
    /**
     * Clicking on the reference element should not close.
     * The reference should handle this itself.
     */
    reference: EventTarget;
    floating: EventTarget;
    inside: boolean;
    outside: boolean;
}

/**
 * Returns a derived store, which translates `MouseEvent`s into a boolean
 * indicating whether they constitue a click that should close `floating`.
 *
 * @param: Should be an event store wrapping document.click.
 */
function isClosingClick(
    store: Readable<MouseEvent>,
    { reference, floating, inside, outside }: ClosingClickArgs,
): Readable<symbol> {
    function isTriggerClick(path: EventTarget[]): boolean {
        return (
            // Reference element was clicked, e.g. the button.
            // The reference element needs to handle opening/closing itself.
            !path.includes(reference) &&
            ((inside && path.includes(floating)) ||
                (outside && !path.includes(floating)))
        );
    }

    function shouldClose(event: MouseEvent): boolean {
        if (isSecondaryButton(event)) {
            return true;
        }

        if (isTriggerClick(event.composedPath())) {
            return true;
        }

        return false;
    }

    return derived(store, (event: MouseEvent, set: (value: symbol) => void): void => {
        if (shouldClose(event)) {
            set(Symbol());
        }
    });
}

export default isClosingClick;
