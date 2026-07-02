// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface CaretLocation {
    coordinates: number[];
    offset: number;
}

export enum Position {
    Before = -1,
    Equal,
    After,
}

/**
 * @returns: Whether first is positioned {before,equal to,after} second
 */
export function compareLocations(
    first: CaretLocation,
    second: CaretLocation,
): Position {
    const smallerLength = Math.min(first.coordinates.length, second.coordinates.length);

    for (let i = 0; i <= smallerLength; i++) {
        if (first.coordinates.length === i) {
            if (second.coordinates.length === i) {
                if (first.offset < second.offset) {
                    return Position.Before;
                } else if (first.offset > second.offset) {
                    return Position.After;
                } else {
                    return Position.Equal;
                }
            }
            return Position.Before;
        } else if (second.coordinates.length === i) {
            return Position.After;
        } else if (first.coordinates[i] < second.coordinates[i]) {
            return Position.Before;
        } else if (first.coordinates[i] > second.coordinates[i]) {
            return Position.After;
        }
    }

    throw new Error("compareLocations: Should never happen");
}
