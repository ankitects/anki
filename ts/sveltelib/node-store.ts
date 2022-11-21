// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { noop } from "@tslib/functional";
import type { Subscriber, Unsubscriber, Updater, Writable } from "svelte/store";

export interface NodeStore<T extends Node> extends Writable<T> {
    setUnprocessed(node: T): void;
}

export function nodeStore<T extends Node>(
    node?: T,
    preprocess: (node: T) => void = noop,
): NodeStore<T> {
    const subscribers: Set<Subscriber<T>> = new Set();

    function setUnprocessed(newNode: T): void {
        if (node && node.isEqualNode(newNode)) {
            return;
        }

        node = newNode;
        for (const subscriber of subscribers) {
            subscriber(node);
        }
    }

    function set(newNode: T): void {
        preprocess(newNode);
        setUnprocessed(newNode);
    }

    function update(fn: Updater<T>): void {
        set(fn(node!));
    }

    function subscribe(subscriber: Subscriber<T>): Unsubscriber {
        subscribers.add(subscriber);

        if (node) {
            subscriber(node);
        }

        return () => subscribers.delete(subscriber);
    }

    return { set, setUnprocessed, update, subscribe };
}

export default nodeStore;
