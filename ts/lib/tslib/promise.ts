// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function promiseWithResolver<T>(): [Promise<T>, (value: T) => void] {
    let resolve: (object: T) => void;
    const promise = new Promise<T>((res) => (resolve = res));

    return [promise, resolve!];
}

/**
 * Runs callbacks one at a time, in the order `run()` was called, regardless
 * of how long each one takes to settle. Useful when independent event
 * handlers race to perform async work (e.g. an RPC call) that must reach
 * some other observer (e.g. a message sent to the backend) in the same
 * order the triggering events originally fired in.
 */
export class SerialQueue {
    private tail: Promise<void> = Promise.resolve();

    run<T>(fn: () => Promise<T> | T): Promise<T> {
        const result = this.tail.then(fn);
        this.tail = result.then(
            () => undefined,
            (error) => {
                console.error("SerialQueue task failed", error);
                return undefined;
            },
        );
        return result;
    }
}
