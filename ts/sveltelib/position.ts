// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Placement } from "@floating-ui/dom";
import { autoUpdate, computePosition, shift, offset, arrow } from "@floating-ui/dom";

const floatingElements: Map<EventTarget, () => void> = new Map();

interface PositionArgs {
    placement: Placement;
    /**
     * The floating element which is positioned relative to `reference`.
     */
    floating: HTMLElement;
    arrow: HTMLElement;
}

function position(
    reference: HTMLElement,
    positionArgs: PositionArgs,
): { update(args: PositionArgs): void; destroy(): void } {
    let args = positionArgs;

    async function updateInner(): Promise<void> {
        const { x, y, middlewareData } = await computePosition(
            reference,
            args.floating,
            {
                middleware: [
                    offset(5),
                    shift({ padding: 5 }),
                    arrow({ element: args.arrow }),
                ],
                placement: args.placement,
            },
        );

        const arrowX = middlewareData.arrow?.x ?? "";

        Object.assign(args.arrow.style, {
            left: `${arrowX}px`,
            top: `-5px`,
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
