// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ComputePositionConfig, FloatingElement, Middleware, Placement, ReferenceElement } from "@floating-ui/dom";
import { arrow, computePosition, flip, hide, inline, offset, shift } from "@floating-ui/dom";

import type { PositionAlgorithm } from "./position-algorithm";

export interface PositionFloatingArgs {
    placement: Placement;
    arrow: HTMLElement;
    shift: number;
    offset: number;
    inline: boolean;
    hideIfEscaped: boolean;
    hideIfReferenceHidden: boolean;
    hideCallback: (reason: string) => void;
}

function positionFloating({
    placement,
    arrow: arrowElement,
    shift: shiftArg,
    offset: offsetArg,
    inline: inlineArg,
    hideIfEscaped,
    hideIfReferenceHidden,
    hideCallback,
}: PositionFloatingArgs): PositionAlgorithm {
    return async function(
        reference: ReferenceElement,
        floating: FloatingElement,
    ): Promise<Placement> {
        const middleware: Middleware[] = [
            flip(),
            offset(offsetArg),
            shift({ padding: shiftArg }),
            arrow({ element: arrowElement, padding: 5 }),
        ];

        if (inlineArg) {
            middleware.unshift(inline());
        }

        const computeArgs: Partial<ComputePositionConfig> = {
            middleware,
            placement,
        };

        if (hideIfEscaped) {
            middleware.push(hide({ strategy: "escaped" }));
        }

        if (hideIfReferenceHidden) {
            middleware.push(hide({ strategy: "referenceHidden" }));
        }

        const {
            x,
            y,
            middlewareData,
            placement: computedPlacement,
        } = await computePosition(reference, floating, computeArgs);

        if (middlewareData.hide?.escaped) {
            hideCallback("escaped");
            return computedPlacement;
        }

        if (middlewareData.hide?.referenceHidden) {
            hideCallback("referenceHidden");
            return computedPlacement;
        }

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

        return computedPlacement;
    };
}

export default positionFloating;
