// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Placement } from "@floating-ui/dom";
import {
    arrow,
    autoUpdate,
    computePosition,
    inline,
    offset,
    shift,
} from "@floating-ui/dom";

export interface PositionArgs {
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
                    inline(),
                    offset(5),
                    shift({ padding: 5 }),
                    arrow({ element: args.arrow, padding: 5 }),
                ],
                placement: args.placement,
            },
        );

        let rotation: number;
        let arrowX: number | undefined;
        let arrowY: number | undefined;

        if (args.placement.startsWith("bottom")) {
            rotation = 45;
            arrowX = middlewareData.arrow?.x;
            arrowY = -5;
        } else if (args.placement.startsWith("left")) {
            rotation = 135;
            arrowX = args.floating!.offsetWidth - 5;
            arrowY = middlewareData.arrow?.y;
        } else if (args.placement.startsWith("top")) {
            rotation = 225;
            arrowX = middlewareData.arrow?.x;
            arrowY = args.floating!.offsetHeight - 5;
        } /* if (args.placement.startsWith("right")) */ else {
            rotation = 315;
            arrowX = -5;
            arrowY = middlewareData.arrow?.y;
        }

        Object.assign(args.arrow.style, {
            left: arrowX ? `${arrowX}px` : "",
            top: arrowY ? `${arrowY}px` : "",
            transform: `rotate(${rotation}deg)`,
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
