// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { derived } from "svelte/store";
import type { Readable } from "svelte/store";

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
    { reference, floating }: ClosingKeyupArgs,
): Readable<symbol> {
    function shouldClose(event: KeyboardEvent) {
        console.log("keyup", event, event.key);
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

// if (/input|select|option|textarea|form/i.test(event.target.tagName))) {

//   const relatedTarget = {
//     relatedTarget: context._element
//   }

//   if (event) {
//     const composedPath = event.composedPath()
//     const isMenuTarget = composedPath.includes(context._menu)
//     if (
//       composedPath.includes(context._element) ||
//       (context._config.autoClose === 'inside' && !isMenuTarget) ||
//       (context._config.autoClose === 'outside' && isMenuTarget)
//     ) {
//       continue
//     }

//     // Tab navigation through the dropdown menu or events from contained inputs shouldn't close the menu
//     if (context._menu.contains(event.target) && ((event.type === 'keyup' && event.key === TAB_KEY) || /input|select|option|textarea|form/i.test(event.target.tagName))) {
//       continue
//     }

//     if (event.type === 'click') {
//       relatedTarget.clickEvent = event
//     }
//   }
