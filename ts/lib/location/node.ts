// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

function getNodeCoordinatesRecursive(
    node: Node,
    coordinates: number[],
    root: Node,
): number[] {
    /* parentNode is Element | Document | DocumentFragment */
    if (node === root || !node.parentNode) {
        return coordinates;
    } else {
        const parent = node.parentNode;
        const newCoordinates = [
            Array.prototype.indexOf.call(node.parentNode.childNodes, node),
            ...coordinates,
        ];
        return getNodeCoordinatesRecursive(parent, newCoordinates, root);
    }
}

export function getNodeCoordinates(node: Node, root: Node): number[] {
    return getNodeCoordinatesRecursive(node, [], root);
}

export function findNodeFromCoordinates(
    coordinates: number[],
    root: Node,
): Node | null {
    if (coordinates.length === 0) {
        return root;
    } else if (!root.childNodes[coordinates[0]]) {
        return null;
    } else {
        const [firstCoordinate, ...restCoordinates] = coordinates;
        return findNodeFromCoordinates(
            restCoordinates,
            root.childNodes[firstCoordinate],
        );
    }
}
