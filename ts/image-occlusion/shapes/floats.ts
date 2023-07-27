// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/** Convert a float to a string with up to 4 fraction digits,
 * which when rounded, reproduces identical pixels to input
 * for up to widths/heights of 10kpx.
 */
export function floatToDisplay(number: number): string {
    if (Number.isNaN(number) || number == 0) {
        return ".0000";
    }
    return number.toFixed(4).replace(/^0+|0+$/g, "");
}
