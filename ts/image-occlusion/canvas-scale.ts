// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Size } from "./types";

/**
 * - On Retina displays, the canvas should be double the size of the CSS pixels
 * to be sharp.
 * - Safari doesn't allow canvas elements to be over 16M (4096x4096), so we need
 * to ensure the canvas is smaller than that size.
 */
export function optimumCanvasSize(imageSize: Size): Size {
    let { width, height } = imageSize;

    const pixelScale = window.devicePixelRatio;
    width *= pixelScale;
    height *= pixelScale;

    const maximumPixels = 4096 * 4096;
    const requiredPixels = width * height;
    if (requiredPixels > maximumPixels) {
        const shrinkScale = Math.sqrt(maximumPixels) / Math.sqrt(requiredPixels);
        width *= shrinkScale;
        height *= shrinkScale;
    }

    return {
        width,
        height,
    };
}
