// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

function getNodeCoordinatesRecursive(node: Node, coordinates: number[]): number[] {
    /* parentNode: Element | Document | DocumentFragment */
    if (!node.parentNode) {
        return coordinates;
    } else {
        const parent = node.parentNode;
        const newCoordinates = [
            Array.prototype.indexOf.call(node.parentNode.childNodes, node),
            ...coordinates,
        ];
        return getNodeCoordinatesRecursive(parent, newCoordinates);
    }
}

export function getNodeCoordinates(node: Node): number[] {
    return getNodeCoordinatesRecursive(node, []);
}

export function findNodeFromCoordinates(
    node: Node,
    coordinates: number[],
): Node | null {
    if (coordinates.length === 0) {
        return node;
    } else if (!node.childNodes[coordinates[0]]) {
        return null;
    } else {
        const [firstCoordinate, ...restCoordinates] = coordinates;
        return findNodeFromCoordinates(
            node.childNodes[firstCoordinate],
            restCoordinates,
        );
    }
}
