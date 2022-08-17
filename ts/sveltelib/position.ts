// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type {
    ComputePositionConfig,
    Placement,
    Middleware,
    ReferenceElement,
    FloatingElement,
} from "@floating-ui/dom";
import {
    arrow,
    autoPlacement,
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
    floating?: FloatingElement;
    placement: Placement | 'auto';
    arrow: HTMLElement;
    shift: number,
    offset: number,
}

function position(
    reference: ReferenceElement,
    positionArgs: PositionArgs,
): { update(args: PositionArgs): void; destroy(): void } {
    let args = positionArgs;
    let cleanup: () => void;

    function destroy(): void {
        cleanup?.();

        if (!args.floating) {
            return;
        }

        args.floating.style.removeProperty("left");
        args.floating.style.removeProperty("top");
    }

    async function updateInner(): Promise<void> {
        if (!args.floating) {
            return;
        }

        const middleware: Middleware[] = [
            inline(),
            offset(args.offset),
            shift({ padding: args.shift }),
            arrow({ element: args.arrow, padding: 5 }),
        ];

        const computeArgs: Partial<ComputePositionConfig> = {
            middleware,
        };

        if (args.placement !== "auto") {
            computeArgs.placement = args.placement;
        } else {
            middleware.push(autoPlacement())
        }

        const { x, y, middlewareData, placement } = await computePosition(
            reference,
            args.floating,
            computeArgs
        );

        console.log(await computePosition(
            reference,
            args.floating,
            computeArgs
        ));

        Object.assign(args.floating.style, {
            left: `${x}px`,
            top: `${y}px`,
        });

        let rotation: number;
        let arrowX: number | undefined;
        let arrowY: number | undefined;

        if (placement.startsWith("bottom")) {
            rotation = 45;
            arrowX = middlewareData.arrow?.x;
            arrowY = -5;
        } else if (placement.startsWith("left")) {
            rotation = 135;
            arrowX = args.floating!.offsetWidth - 5;
            arrowY = middlewareData.arrow?.y;
        } else if (placement.startsWith("top")) {
            rotation = 225;
            arrowX = middlewareData.arrow?.x;
            arrowY = args.floating!.offsetHeight - 5;
        } /* if (placement.startsWith("right")) */ else {
            rotation = 315;
            arrowX = -5;
            arrowY = middlewareData.arrow?.y;
        }

        Object.assign(args.arrow.style, {
            left: arrowX ? `${arrowX}px` : "",
            top: arrowY ? `${arrowY}px` : "",
            transform: `rotate(${rotation}deg)`,
        });
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
