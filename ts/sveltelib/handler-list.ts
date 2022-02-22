// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Readable, Writable } from "svelte/store";
import { writable } from "svelte/store";

import type { Callback } from "../lib/typing";

type Handler<T, U> = (args: T) => U;

interface HandlerAccess<T, U> {
    callback: Handler<T, U>;
    clear(): void;
}

class TriggerItem<T, U> {
    #active: Writable<boolean>;

    constructor(
        private setter: (handler: Handler<T, U>, clear: Callback) => void,
        private clear: Callback,
    ) {
        this.#active = writable(false);
    }

    /**
     * A store which indicates whether the trigger is currently turned on.
     */
    get active(): Readable<boolean> {
        return this.#active;
    }

    /**
     * Deactivate the trigger. Can be safely called multiple times.
     */
    off() {
        this.#active.set(false);
        this.clear();
    }

    on(handler: Handler<T, U>): void {
        this.setter(handler, () => this.off());
        this.#active.set(true);
    }
}

interface HandlerOptions {
    once: boolean;
}

export class Handlers<T, U> {
    #list: HandlerAccess<T, U>[] = [];

    trigger(options?: Partial<HandlerOptions>): TriggerItem<T, U> {
        const once = options?.once ?? false;
        let handler: Handler<T, U> | null = null;

        return new TriggerItem(
            (callback: Handler<T, U>, doClear: Callback): void => {
                handler = callback;

                const clear = (): void => {
                    if (once) {
                        doClear();
                    }
                };

                this.#list.push({
                    callback(args: T): U {
                        const result = callback(args);
                        clear();
                        return result;
                    },
                    clear,
                });
            },
            () => {
                if (handler) {
                    this.off(handler);
                    handler = null;
                }
            },
        );
    }

    on(handler: Handler<T, U>, options?: Partial<HandlerOptions>): Callback {
        const once = options?.once ?? false;

        const clear = (): void => {
            if (once) {
                this.off(handler);
            }
        };

        const handlerAccess = {
            callback: (args: T): U => {
                const result = handler(args);
                clear();
                return result;
            },
            clear,
        };

        this.#list.push(handlerAccess);

        return () => {
            this.off(handler);
        };
    }

    private off(handler: Handler<T, U>): void {
        const index = this.#list.indexOf(handler);

        if (index >= 0) {
            this.#list.splice(index, 1);
        }
    }

    get length(): number {
        return this.#list.length;
    }

    [Symbol.iterator](): Iterator<Handler<T, U>, null, unknown> {
        const list = this.#list;
        let step = 0;

        return {
            next(): IteratorResult<Handler<T, U>, null> {
                if (step >= list.length) {
                    return { value: null, done: true };
                }

                return { value: list[step++], done: false };
            },
        };
    }
}
