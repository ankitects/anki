// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Placement } from "@floating-ui/dom";
import { arrow, autoUpdate, computePosition, offset, shift } from "@floating-ui/dom";

interface PositionArgs {
    /**
     * The floating element which is positioned relative to `reference`.
     */
    floating: HTMLElement | null;
    placement: Placement;
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
            args.floating!,
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

        Object.assign(args.floating!.style, {
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

        args.floating.style.removeProperty("left");
        args.floating.style.removeProperty("top");
    }

    function update(updateArgs: PositionArgs): void {
        destroy();
        args = updateArgs;

        if (!args.floating) {
            return;
        }

        cleanup = autoUpdate(reference, args.floating, updateInner);
    }

    update(args);

    return {
        update,
        destroy,
    };
}

export default position;
