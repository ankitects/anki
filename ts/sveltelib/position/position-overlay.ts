// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { ComputePositionConfig, FloatingElement, Middleware, Placement, ReferenceElement } from "@floating-ui/dom";
import { computePosition, inline, offset } from "@floating-ui/dom";

import type { PositionAlgorithm } from "./position-algorithm";

export interface PositionOverlayArgs {
    padding: number;
    inline: boolean;
    hideCallback: (reason: string) => void;
}

function positionOverlay({
    padding,
    inline: inlineArg,
    hideCallback,
}: PositionOverlayArgs): PositionAlgorithm {
    return async function(
        reference: ReferenceElement,
        floating: FloatingElement,
    ): Promise<Placement> {
        const middleware: Middleware[] = inlineArg ? [inline()] : [];

        const { width, height } = reference.getBoundingClientRect();

        middleware.push(
            offset({
                mainAxis: -(height + padding),
            }),
        );

        const computeArgs: Partial<ComputePositionConfig> = {
            middleware,
        };

        const { x, y, middlewareData, placement } = await computePosition(
            reference,
            floating,
            computeArgs,
        );

        // console.log(x, y)

        if (middlewareData.hide?.escaped) {
            hideCallback("escaped");
        }

        if (middlewareData.hide?.referenceHidden) {
            hideCallback("referenceHidden");
        }

        Object.assign(floating.style, {
            left: `${x}px`,
            top: `${y}px`,
            width: `${width + 2 * padding}px`,
            height: `${height + 2 * padding}px`,
        });

        return placement;
    };
}

export default positionOverlay;
