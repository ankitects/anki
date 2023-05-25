// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/** Position normalized to 0-1 range, e.g. 150px in a 600x300px canvas is 0.25 */
export function xToNormalized(canvas: HTMLCanvasElement, x: number): string {
    return floatToDisplay(x / canvas.width);
}

/** Position normalized to 0-1 range, e.g. 150px in a 600x300px canvas is 0.5 */
export function yToNormalized(canvas: HTMLCanvasElement, y: number): string {
    return floatToDisplay(y / canvas.height);
}

/** Position in pixels from normalized range, e.g 0.25 in a 600x300px canvas is 150. */
export function xFromNormalized(canvas: HTMLCanvasElement, x: string): number {
    return Math.round(parseFloat(x) * canvas.width);
}

/** Position in pixels from normalized range, e.g 0.5 in a 600x300px canvas is 150. */
export function yFromNormalized(canvas: HTMLCanvasElement, y: string): number {
    return Math.round(parseFloat(y) * canvas.height);
}

/** Convert a float to a string with up to 4 fraction digits,
 * which when rounded, reproduces identical pixels to input
 * for up to widths/heights of 10kpx.
 */
function floatToDisplay(number: number): string {
    return number.toFixed(4).replace(/^0+|0+$/g, "");
}
