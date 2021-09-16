// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export interface CaretLocation {
    coordinates: number[];
    offset: number;
}

export enum Order {
    LessThan,
    Equal,
    GreaterThan,
}

export function compareLocations(first: CaretLocation, second: CaretLocation): Order {
    for (let i = 0; true; i++) {
        if (first.coordinates.length === i) {
            if (second.coordinates.length === i) {
                if (first.offset < second.offset) {
                    return Order.LessThan;
                } else if (first.offset > second.offset) {
                    return Order.GreaterThan;
                } else {
                    return Order.Equal;
                }
            }
            return Order.LessThan;
        } else if (second.coordinates.length === i) {
            return Order.GreaterThan;
        } else if (first.coordinates[i] < second.coordinates[i]) {
            return Order.LessThan;
        } else if (first.coordinates[i] > second.coordinates[i]) {
            return Order.GreaterThan;
        }
    }
}
