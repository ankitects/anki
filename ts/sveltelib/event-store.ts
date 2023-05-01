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

const documentClick = eventStore(document, "click", MouseEvent);
const documentKeyup = eventStore(document, "keyup", KeyboardEvent);

export { documentClick, documentKeyup };
