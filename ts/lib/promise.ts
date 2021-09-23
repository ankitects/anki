// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function promiseResolve<T>(): [Promise<T>, (value: T) => void] {
    let resolve: (object: T) => void;
    const promise = new Promise<T>((res) => (resolve = res));

    return [promise, resolve!];
}
