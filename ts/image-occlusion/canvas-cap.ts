// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * Safari doesn't allow canvas elements to be over 16M (4096x4096), so we need
 * to cap the canvas size to a smaller value.
 */
export function cappedCanvasSize(
    size: { width: number; height: number },
): { width: number; height: number; scalar: number } {
    const maximumPixels = 1000000;
    const { width, height } = size;

    const requiredPixels = width * height;
    if (requiredPixels <= maximumPixels) return { width, height, scalar: 1 };

    const scalar = Math.sqrt(maximumPixels) / Math.sqrt(requiredPixels);
    return {
        width: Math.floor(width * scalar),
        height: Math.floor(height * scalar),
        scalar: scalar,
    };
}
