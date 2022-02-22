// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Readable, Writable } from "svelte/store";
import { writable } from "svelte/store";

import type { Callback } from "../lib/typing";

type Handler<T> = (args: T) => Promise<void>;

interface HandlerAccess<T> {
    callback: Handler<T>;
    clear(): void;
}

class TriggerItem<T> {
    #active: Writable<boolean>;

    constructor(
        private setter: (handler: Handler<T>, clear: Callback) => void,
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
    off(): void {
        this.#active.set(false);
        this.clear();
    }

    on(handler: Handler<T>): void {
        this.setter(handler, () => this.off());
        this.#active.set(true);
    }
}

interface HandlerOptions {
    once: boolean;
}

export class HandlerList<T> {
    #list: HandlerAccess<T>[] = [];

    trigger(options?: Partial<HandlerOptions>): TriggerItem<T> {
        const once = options?.once ?? false;
        let handler: Handler<T> | null = null;

        return new TriggerItem(
            (callback: Handler<T>, doClear: Callback): void => {
                const handlerAccess = {
                    callback(args: T): Promise<void> {
                        const result = callback(args);
                        if (once) {
                            doClear();
                        }
                        return result;
                    },
                    clear(): void {
                        if (once) {
                            doClear();
                        }
                    },
                };

                this.#list.push(handlerAccess);
                handler = handlerAccess.callback;
            },
            () => {
                if (handler) {
                    this.off(handler);
                    handler = null;
                }
            },
        );
    }

    on(handler: Handler<T>, options?: Partial<HandlerOptions>): Callback {
        const once = options?.once ?? false;
        let offHandler: Handler<T> | null = null;

        const off = (): void => {
            if (offHandler) {
                this.off(offHandler);
                offHandler = null;
            }
        };

        const handlerAccess = {
            callback: (args: T): Promise<void> => {
                const result = handler(args);
                if (once) {
                    off();
                }
                return result;
            },
            clear(): void {
                if (once) {
                    off();
                }
            },
        };

        offHandler = handlerAccess.callback;

        this.#list.push(handlerAccess);
        return off;
    }

    private off(handler: Handler<T>): void {
        const index = this.#list.findIndex(
            (value: HandlerAccess<T>): boolean => value.callback === handler,
        );

        if (index >= 0) {
            this.#list.splice(index, 1);
        }
    }

    get length(): number {
        return this.#list.length;
    }

    dispatch(args: T): Promise<void> {
        const promises: Promise<void>[] = [];

        for (const { callback } of [...this]) {
            promises.push(callback(args));
        }

        return Promise.all(promises) as unknown as Promise<void>;
    }

    clear(): void {
        for (const { clear } of [...this]) {
            clear();
        }
    }

    [Symbol.iterator](): Iterator<HandlerAccess<T>, null, unknown> {
        const list = this.#list;
        let step = 0;

        return {
            next(): IteratorResult<HandlerAccess<T>, null> {
                if (step >= list.length) {
                    return { value: null, done: true };
                }

                return { value: list[step++], done: false };
            },
        };
    }
}

export type { TriggerItem };
