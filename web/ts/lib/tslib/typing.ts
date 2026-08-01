// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function assertUnreachable(x: never): never {
    throw new Error(`unreachable: ${x}`);
}

export type Callback = () => void;
export type AsyncCallback = () => Promise<void>;

export function singleCallback(...callbacks: Callback[]): Callback {
    return () => {
        for (const cb of callbacks) {
            cb();
        }
    };
}
