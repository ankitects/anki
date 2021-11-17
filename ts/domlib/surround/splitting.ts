// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * will split existing elements along the anchor and focus
 * will not surround, if it would need to break a block element
 * can be used for inline elements e.g. <ruby><rb>, or <anki-mathjax>
 **/
export function surroundSplitting(
    range: Range,
    surroundNode: Node,
    coordinatesIntoSurroundNode: number[],
): Node | null {
    return null;
}
