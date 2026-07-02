// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

function getNodeCoordinatesRecursive(
    node: Node,
    base: Node,
    coordinates: number[],
): number[] {
    /* parentNode: Element | Document | DocumentFragment */
    if (!node.parentNode || node === base) {
        return coordinates;
    } else {
        const parent = node.parentNode;
        const newCoordinates = [
            Array.prototype.indexOf.call(node.parentNode.childNodes, node),
            ...coordinates,
        ];
        return getNodeCoordinatesRecursive(parent, base, newCoordinates);
    }
}

export function getNodeCoordinates(node: Node, base: Node): number[] {
    return getNodeCoordinatesRecursive(node, base, []);
}

export function findNodeFromCoordinates(
    base: Node,
    coordinates: number[],
): Node | null {
    if (coordinates.length === 0) {
        return base;
    } else if (!base.childNodes[coordinates[0]]) {
        return null;
    } else {
        const [firstCoordinate, ...restCoordinates] = coordinates;
        return findNodeFromCoordinates(
            base.childNodes[firstCoordinate],
            restCoordinates,
        );
    }
}
