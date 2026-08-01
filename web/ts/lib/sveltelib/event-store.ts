// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { EventTargetToMap } from "@tslib/events";
import { on } from "@tslib/events";
import type { Callback } from "@tslib/typing";
import type { Readable, Subscriber } from "svelte/store";
import { readable } from "svelte/store";

type Init<T> = { new(type: string): T; prototype: T };

/**
 * A store wrapping an event. Automatically adds/removes event handler upon
 * first/last subscriber.
 *
 * @remarks
 * Should probably always be used in conjunction with `subscribeToUpdates`.
 */
function eventStore<T extends EventTarget, K extends keyof EventTargetToMap<T>>(
    target: T,
    eventType: Exclude<K, symbol | number>,
    /**
     * Store needs an initial value. This should probably be a freshly
     * constructed event, e.g. `new MouseEvent("click")`.
     */
    constructor: Init<EventTargetToMap<T>[K]>,
): Readable<EventTargetToMap<T>[K]> {
    const initEvent = new constructor(eventType);
    return readable(
        initEvent,
        (set: Subscriber<EventTargetToMap<T>[K]>): Callback => on(target, eventType, set),
    );
}

export default eventStore;

/**
 * A click event that fires only if the mouse has not appreciably moved since the button
 * was pressed down. This was added so that if the user clicks inside a floating area and
 * drags the mouse outside the area while selecting text, it doesn't end up closing the
 * floating area.
 */
function mouseClickWithoutDragStore(): Readable<MouseEvent> {
    const initEvent = new MouseEvent("click");

    return readable(
        initEvent,
        (set: Subscriber<MouseEvent>): Callback => {
            let startingX: number;
            let startingY: number;
            function onMouseDown(evt: MouseEvent): void {
                startingX = evt.clientX;
                startingY = evt.clientY;
            }
            function onClick(evt: MouseEvent): void {
                if (Math.abs(startingX - evt.clientX) < 5 && Math.abs(startingY - evt.clientY) < 5) {
                    set(evt);
                }
            }
            document.addEventListener("mousedown", onMouseDown);
            document.addEventListener("click", onClick);
            return () => {
                document.removeEventListener("click", onClick);
                document.removeEventListener("mousedown", onMouseDown);
            };
        },
    );
}

const documentClick = mouseClickWithoutDragStore();
const documentKeyup = eventStore(document, "keyup", KeyboardEvent);

export { documentClick, documentKeyup };
