// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function removeNode(element: Node): void {
    element.parentNode?.removeChild(element);
}

function iterateElement(
    filter: (node: Node) => void,
    fragment: DocumentFragment | Element,
): void {
    for (const child of [...fragment.childNodes]) {
        filter(child);
    }
}

export const filterNode = (elementFilter: (element: Element) => void) => (node: Node): void => {
    switch (node.nodeType) {
        case Node.COMMENT_NODE:
            removeNode(node);
            break;

        case Node.DOCUMENT_FRAGMENT_NODE:
            iterateElement(filterNode(elementFilter), node as DocumentFragment);
            break;

        case Node.ELEMENT_NODE:
            iterateElement(filterNode(elementFilter), node as Element);
            elementFilter(node as Element);
            break;

        default:
            // do nothing
    }
};
