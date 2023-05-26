// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Size } from "./types";

/** Position normalized to 0-1 range, e.g. 150px in a 600x300px canvas is 0.25 */
export function xToNormalized(size: Size, x: number): string {
    return floatToDisplay(x / size.width);
}

/** Position normalized to 0-1 range, e.g. 150px in a 600x300px canvas is 0.5 */
export function yToNormalized(size: Size, y: number): string {
    return floatToDisplay(y / size.height);
}

/** Position in pixels from normalized range, e.g 0.25 in a 600x300px canvas is 150. */
export function xFromNormalized(size: Size, x: string): number {
    return Math.round(parseFloat(x) * size.width);
}

/** Position in pixels from normalized range, e.g 0.5 in a 600x300px canvas is 150. */
export function yFromNormalized(size: Size, y: string): number {
    return Math.round(parseFloat(y) * size.height);
}

/** Convert a float to a string with up to 4 fraction digits,
 * which when rounded, reproduces identical pixels to input
 * for up to widths/heights of 10kpx.
 */
function floatToDisplay(number: number): string {
    return number.toFixed(4).replace(/^0+|0+$/g, "");
}
