// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Size } from "./types";

/**
 * - Choose an appropriate size for the canvas based on the current container,
 * so the masks are sharp and legible.
 * - Safari doesn't allow canvas elements to be over 16M (4096x4096), so we need
 * to ensure the canvas is smaller than that size.
 * - Returns the size in actual pixels, not CSS size.
 */
export function optimumPixelSizeForCanvas(imageSize: Size, containerSize: Size): Size {
    let { width, height } = imageSize;

    const pixelScale = window.devicePixelRatio;
    containerSize.width *= pixelScale;
    containerSize.height *= pixelScale;

    // Scale image dimensions to fit in container, retaining aspect ratio.
    // We take the minimum of width/height scales, as that's the one that is
    // potentially limiting the image from expanding.
    const containerScale = Math.min(containerSize.width / imageSize.width, containerSize.height / imageSize.height);
    width *= containerScale;
    height *= containerScale;

    const maximumPixels = 4096 * 4096;
    const requiredPixels = width * height;
    if (requiredPixels > maximumPixels) {
        const shrinkScale = Math.sqrt(maximumPixels) / Math.sqrt(requiredPixels);
        width *= shrinkScale;
        height *= shrinkScale;
    }

    return {
        width: Math.floor(width),
        height: Math.floor(height),
    };
}

/** See {@link optimumPixelSizeForCanvas()} */
export function optimumCssSizeForCanvas(imageSize: Size, containerSize: Size): Size {
    const { width, height } = optimumPixelSizeForCanvas(imageSize, containerSize);
    return {
        width: width / window.devicePixelRatio,
        height: height / window.devicePixelRatio,
    };
}
