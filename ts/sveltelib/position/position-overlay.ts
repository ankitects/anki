// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type {
    ComputePositionConfig,
    FloatingElement,
    Middleware,
} from "@floating-ui/dom";
import {
    computePosition,
    inline,
} from "@floating-ui/dom";

import type { PositionAlgorithm } from "./position-algorithm"

export interface PositionOverlayArgs {
    padding: number,
    hideCallback: (reason: symbol) => void,
}

function positionOverlay({
    padding,
    hideCallback,
}: PositionOverlayArgs): PositionAlgorithm {
    return async function(reference: HTMLElement, floating: FloatingElement): Promise<void> {
        const middleware: Middleware[] = [
            inline(),
        ];

        const computeArgs: Partial<ComputePositionConfig> = {
            middleware,
        };

        const { middlewareData } = await computePosition(
            reference,
            floating,
            computeArgs
        );

        if (middlewareData.hide?.escaped) {
            hideCallback(Symbol("escaped"));
        }

        if (middlewareData.hide?.referenceHidden) {
            hideCallback(Symbol("referenceHidden"));
        }


        const { x, y, width, height } = reference.getBoundingClientRect();

        Object.assign(floating.style, {
            left: `${x - padding}px`,
            top: `${y - padding}px`,
            width: `${width + 2 * padding}px`,
            height: `${height + 2 * padding}px`,
        });
    }
}

export default positionOverlay;
