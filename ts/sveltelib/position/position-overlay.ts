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
import type { Writable } from "svelte/store";

import type { PositionAlgorithm } from "./position-algorithm"

export interface PositionOverlayArgs {
    show: Writable<boolean>,
}

function positionOverlay({
    show,
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

        const { x, y, width, height } = reference.getBoundingClientRect();

        if (middlewareData.hide?.escaped || middlewareData.hide?.referenceHidden) {
            show.set(false);
        }

        Object.assign(floating.style, {
            left: `${x}px`,
            top: `${y}px`,
            width: `${width}px`,
            height: `${height}px`,
        });
    }
}

export default positionOverlay;
