// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

export function noop(): void {
    /* noop */
}

export async function asyncNoop(): Promise<void> {
    /* noop */
}

export function id<T>(t: T): T {
    return t;
}

export function truthy<T>(t: T | void | undefined | null): t is T {
    return Boolean(t);
}
