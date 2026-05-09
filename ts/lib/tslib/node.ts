// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function isOnlyChild(node: Node): boolean {
    return node.parentNode!.childNodes.length === 1;
}

export function hasOnlyChild(node: Node): boolean {
    return node.childNodes.length === 1;
}

export function ascend(node: Node): Node {
    return node.parentNode!;
}
