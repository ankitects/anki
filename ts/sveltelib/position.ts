// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Placement } from "@floating-ui/dom";
import { autoUpdate, computePosition } from "@floating-ui/dom";

const floatingElements: Map<EventTarget, () => void> = new Map();

interface PositionArgs {
    /**
     * The floating element which is positioned relative to `reference`.
     */
    floating: HTMLElement;
    placement: Placement;
}

function position(
    reference: HTMLElement,
    positionArgs: PositionArgs,
): { update(args: PositionArgs): void; destroy(): void } {
    let args = positionArgs;

    async function updateInner(): Promise<void> {
        const { x, y } = await computePosition(reference, args.floating, {
            placement: args.placement,
        });

        Object.assign(args.floating.style, {
            left: `${x}px`,
            top: `${y}px`,
        });
    }

    let cleanup: (() => void) | null = null;

    function destroy(): void {
        cleanup?.();
        cleanup = null;

        if (!args.floating) {
            return;
        }

        floatingElements.delete(args.floating);
    }

    function update(updateArgs: PositionArgs): void {
        destroy();
        args = updateArgs;

        if (!args.floating) {
            return;
        }

        cleanup = autoUpdate(reference, args.floating, updateInner);
        floatingElements.set(args.floating, destroy);
    }

    update(args);

    return {
        update,
        destroy,
    };
}

export default position;
