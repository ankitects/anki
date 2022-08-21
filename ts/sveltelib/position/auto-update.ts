// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { FloatingElement } from "@floating-ui/dom";
import { autoUpdate as floatingUiAutoUpdate } from "@floating-ui/dom";
import type { ActionReturn } from "svelte/action";

import type { Callback } from "../../lib/typing";

/**
 * The interface of `autoUpdate` of floating-ui.
 * This means PositioningCallback can be used with that, but also invoked as it is.
 *
 * @example ```
 * // Invoke the positioning algorithm handily
 * position(myReference, (_, _, callback) => {
 *     callback();
 * })`
 */
export type PositioningCallback = (
    reference: HTMLElement,
    floating: FloatingElement,
    position: Callback,
) => Callback;

/**
 * The interface of a function that calls `computePosition` of floating-ui.
 */
export type PositionFunc = (
    reference: HTMLElement,
    callback: PositioningCallback,
) => Callback;

function autoUpdate(
    reference: HTMLElement,
    /**
     * The method to position the floating element.
     */
    position: PositionFunc,
): ActionReturn<PositionFunc> {
    let cleanup: Callback;

    function destroy() {
        cleanup?.();
    }

    function update(position: PositionFunc): void {
        destroy();
        cleanup = position(reference, floatingUiAutoUpdate);
    }

    update(position);

    return { destroy, update };
}

export default autoUpdate;
