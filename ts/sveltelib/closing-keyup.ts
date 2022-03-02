// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Readable } from "svelte/store";
import { derived } from "svelte/store";

interface ClosingKeyupArgs {
    /**
     * Clicking on the reference element should not close.
     * The reference should handle this itself.
     */
    reference: Node;
    floating: Node;
}

/**
 * Returns a derived store, which translates `MouseEvent`s into a boolean
 * indicating whether they constitue a click that should close `floating`.
 *
 * @param: Should be an event store wrapping document.click.
 */
function isClosingKeyup(
    store: Readable<KeyboardEvent>,
    _args: ClosingKeyupArgs,
): Readable<symbol> {
    // TODO there needs to be special treatment, whether the keyup happens
    // inside the floating element or outside, but I'll defer until we actually
    // use this for a popover with an input field
    function shouldClose(event: KeyboardEvent) {
        if (event.key === "Tab") {
            // Allow Tab navigation.
            return false;
        }

        return true;
    }

    return derived(
        store,
        (event: KeyboardEvent, set: (value: symbol) => void): void => {
            if (shouldClose(event)) {
                set(Symbol());
            }
        },
    );
}

export default isClosingKeyup;
