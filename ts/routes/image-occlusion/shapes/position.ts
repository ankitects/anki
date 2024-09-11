// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Size } from "../types";

/** Position normalized to 0-1 range, e.g. 150px in a 600x300px canvas is 0.25 */
export function xToNormalized(size: Size, x: number): number {
    return x / size.width;
}

/** Position normalized to 0-1 range, e.g. 150px in a 600x300px canvas is 0.5 */
export function yToNormalized(size: Size, y: number): number {
    return y / size.height;
}

/** Position in pixels from normalized range, e.g 0.25 in a 600x300px canvas is 150. */
export function xFromNormalized(size: Size, x: number): number {
    return Math.round(x * size.width);
}

/** Position in pixels from normalized range, e.g 0.5 in a 600x300px canvas is 150. */
export function yFromNormalized(size: Size, y: number): number {
    return Math.round(y * size.height);
}
