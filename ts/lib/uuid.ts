// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/**
 * TODO replace with crypto.randomUUID
 */
export function randomUUID(): string {
    const value = `${1e7}-${1e3}-${4e3}-${8e3}-${1e11}`;

    return value.replace(/[018]/g, (character: string): string =>
        (
            Number(character)
            ^ (crypto.getRandomValues(new Uint8Array(1))[0]
                & (15 >> (Number(character) / 4)))
        ).toString(16));
}
