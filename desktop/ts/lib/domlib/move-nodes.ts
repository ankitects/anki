// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { nodeIsElement, nodeIsText } from "@tslib/dom";

import { placeCaretAfter } from "./place-caret";

export function moveChildOutOfElement(
    element: Element,
    child: Node,
    placement: "beforebegin" | "afterend",
): Node {
    if (child.isConnected) {
        child.parentNode!.removeChild(child);
    }

    let referenceNode: Node;

    if (nodeIsElement(child)) {
        referenceNode = element.insertAdjacentElement(placement, child)!;
    } else if (nodeIsText(child)) {
        element.insertAdjacentText(placement, child.wholeText);
        referenceNode = placement === "beforebegin"
            ? element.previousSibling!
            : element.nextSibling!;
    } else {
        throw "moveChildOutOfElement: unsupported";
    }

    return referenceNode;
}

export function moveNodesInsertedOutside(element: Element, allowedChild: Node): void {
    if (element.childNodes.length === 1) {
        return;
    }

    const childNodes = [...element.childNodes];
    const allowedIndex = childNodes.findIndex((child) => child === allowedChild);

    const beforeChildren = childNodes.slice(0, allowedIndex);
    const afterChildren = childNodes.slice(allowedIndex + 1);

    // Special treatment for pressing return after mathjax block
    if (
        afterChildren.length === 2
        && afterChildren.every((child) => (child as Element).tagName === "BR")
    ) {
        const first = afterChildren.pop();
        element.removeChild(first!);
    }

    let lastNode: Node | null = null;

    for (const node of beforeChildren) {
        lastNode = moveChildOutOfElement(element, node, "beforebegin");
    }

    for (const node of afterChildren) {
        lastNode = moveChildOutOfElement(element, node, "afterend");
    }

    if (lastNode) {
        placeCaretAfter(lastNode);
    }
}
