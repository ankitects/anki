// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Readable } from "svelte/store";
import { derived } from "svelte/store";

import type { EventPredicateResult } from "./event-predicate";

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
 * indicating whether they constitute a click that should close `floating`.
 *
 * @param store: Should be an event store wrapping document.click.
 */
function isClosingClick(
    store: Readable<MouseEvent>,
    { reference, floating, inside, outside }: ClosingClickArgs,
): Readable<EventPredicateResult> {
    function isTriggerClick(path: EventTarget[]): string | false {
        // Reference element was clicked, e.g. the button.
        // The reference element needs to handle opening/closing itself.
        if (path.includes(reference)) {
            return false;
        }

        if (inside && path.includes(floating)) {
            return "insideClick";
        }

        if (outside && !path.includes(floating)) {
            return "outsideClick";
        }

        return false;
    }

    function shouldClose(event: MouseEvent): string | false {
        if (isSecondaryButton(event)) {
            return "secondaryButton";
        }

        return isTriggerClick(event.composedPath());
    }

    return derived(
        store,
        (event: MouseEvent, set: (value: EventPredicateResult) => void): void => {
            const reason = shouldClose(event);

            if (reason) {
                set({ reason, originalEvent: event });
            }
        },
    );
}

export default isClosingClick;
