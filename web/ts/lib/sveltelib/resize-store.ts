// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Callback } from "@tslib/typing";
import type { Readable, Subscriber } from "svelte/store";
import { readable } from "svelte/store";

interface ResizeObserverArgs {
    entries: ResizeObserverEntry[];
    observer: ResizeObserver;
}

export type ResizeStore = Readable<ResizeObserverArgs>;

/**
 * A store wrapping a ResizeObserver. Automatically observes the target upon
 * first/last subscriber.
 *
 * @remarks
 * Should probably always be used in conjunction with `subscribeToUpdates`.
 */
function resizeStore(target: Element): ResizeStore {
    let setter: (args: ResizeObserverArgs) => void;

    const observer = new ResizeObserver(
        (entries: ResizeObserverEntry[], observer: ResizeObserver): void =>
            setter({
                entries,
                observer,
            }),
    );

    return readable(
        { entries: [], observer },
        (set: Subscriber<ResizeObserverArgs>): Callback => {
            setter = set;
            observer.observe(target);

            return () => observer.unobserve(target);
        },
    );
}

export default resizeStore;
