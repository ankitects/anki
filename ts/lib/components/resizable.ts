// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Writable } from "svelte/store";
import { writable } from "svelte/store";

export interface Resizer {
    start(): void;

    /**
     * @returns Actually applied resize. If the resizedWidth is too small,
     * no resize can be applied anymore.
     */
    resize(increment: number): number;
    setSize(size: number): void;
    stop(fullWidth: number, amount: number): void;
}

interface ResizedStores {
    resizesDimension: Writable<boolean>;
    resizedDimension: Writable<number>;
}

type ResizableResult = [
    ResizedStores,
    (element: HTMLElement, getter: (element: HTMLElement) => number) => void,
    Resizer,
];

export function resizable(
    baseSize: number,
    resizes: Writable<boolean>,
    paneSize: Writable<number>,
): ResizableResult {
    const resizesDimension = writable(false);
    const resizedDimension = writable(0);

    let pane: HTMLElement;
    let getter: (element: HTMLElement) => number;

    let dimension = 0;

    function resizeAction(
        element: HTMLElement,
        getValue: (element: HTMLElement) => number,
    ): void {
        pane = element;
        getter = getValue;
    }

    function start() {
        resizes.set(true);
        resizesDimension.set(true);

        dimension = getter(pane);
        resizedDimension.set(dimension);
    }

    function resize(increment = 0): number {
        if (dimension + increment < 0) {
            const applied = -dimension;
            dimension = 0;
            resizedDimension.set(dimension);
            return applied;
        }

        dimension += increment;
        resizedDimension.set(dimension);
        return increment;
    }

    function setSize(size = 0): void {
        paneSize.set(size);
    }

    function stop(fullDimension: number, amount: number): void {
        paneSize.set((dimension / fullDimension) * amount * baseSize);
        resizesDimension.set(false);
        resizes.set(false);
    }

    return [
        { resizesDimension, resizedDimension },
        resizeAction,
        { start, resize, setSize, stop },
    ];
}
