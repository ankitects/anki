// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type {
    ComputePositionConfig,
    FloatingElement,
    Middleware,
    Placement,
} from "@floating-ui/dom";
import {
    arrow,
    autoPlacement,
    computePosition,
    inline,
    offset,
    shift,
} from "@floating-ui/dom";

/**
 * The interface of a function that calls `computePosition` of floating-ui.
 */
export type PositionAlgorithm = (reference: HTMLElement, floating: FloatingElement) => Promise<void>;

export interface PositionFloatingArgs {
    placement: Placement | 'auto';
    arrow: HTMLElement;
    shift: number,
    offset: number,
}

function positionFloating({
    placement,
    arrow: arrowElement,
    shift: shiftArg,
    offset: offsetArg,
}: PositionFloatingArgs): PositionAlgorithm {
    return async function(reference: HTMLElement, floating: FloatingElement): Promise<void> {
        const middleware: Middleware[] = [
            inline(),
            offset(offsetArg),
            shift({ padding: shiftArg }),
            arrow({ element: arrowElement, padding: 5 }),
        ];

        const computeArgs: Partial<ComputePositionConfig> = {
            middleware,
        };

        if (placement !== "auto") {
            computeArgs.placement = placement;
        } else {
            middleware.push(autoPlacement())
        }

        const { x, y, middlewareData, placement: computedPlacement } = await computePosition(
            reference,
            floating,
            computeArgs
        );

        Object.assign(floating.style, {
            left: `${x}px`,
            top: `${y}px`,
        });

        let rotation: number;
        let arrowX: number | undefined;
        let arrowY: number | undefined;

        if (computedPlacement.startsWith("bottom")) {
            rotation = 45;
            arrowX = middlewareData.arrow?.x;
            arrowY = -5;
        } else if (computedPlacement.startsWith("left")) {
            rotation = 135;
            arrowX = floating.offsetWidth - 5;
            arrowY = middlewareData.arrow?.y;
        } else if (computedPlacement.startsWith("top")) {
            rotation = 225;
            arrowX = middlewareData.arrow?.x;
            arrowY = floating.offsetHeight - 5;
        } /* if (computedPlacement.startsWith("right")) */ else {
            rotation = 315;
            arrowX = -5;
            arrowY = middlewareData.arrow?.y;
        }

        Object.assign(arrowElement.style, {
            left: arrowX ? `${arrowX}px` : "",
            top: arrowY ? `${arrowY}px` : "",
            transform: `rotate(${rotation}deg)`,
        });
    }
}

export default positionFloating;
