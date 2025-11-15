// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { on } from "./events";
import type { Modifier } from "./keys";
import { checkIfModifierKey, checkModifiers, keyToPlatformString, modifiersToPlatformString } from "./keys";
import { registerPackage } from "./runtime-require";

const keyCodeLookup = {
    Backspace: 8,
    Delete: 46,
    Tab: 9,
    Enter: 13,
    F1: 112,
    F2: 113,
    F3: 114,
    F4: 115,
    F5: 116,
    F6: 117,
    F7: 118,
    F8: 119,
    F9: 120,
    F10: 121,
    F11: 122,
    F12: 123,
    "=": 187,
    "-": 189,
    "[": 219,
    "]": 221,
    "\\": 220,
    ";": 186,
    "'": 222,
    ",": 188,
    ".": 190,
    "/": 191,
    "`": 192,
    "!": 49,
    "*": 56,
    "@": 50,
} as const;

function isRequiredModifier(modifier: string): boolean {
    return !modifier.endsWith("?");
}

function splitKeyCombinationString(keyCombinationString: string): string[][] {
    return keyCombinationString.split(", ").map((segment) => segment.split("+"));
}

function toPlatformString(keyCombination: string[]): string {
    return (
        modifiersToPlatformString(
            keyCombination.slice(0, -1).filter(isRequiredModifier),
        ) + keyToPlatformString(keyCombination[keyCombination.length - 1])
    );
}

export function getPlatformString(keyCombinationString: string): string {
    return splitKeyCombinationString(keyCombinationString)
        .map(toPlatformString)
        .join(", ");
}

function checkKey(event: KeyboardEvent, key: number): boolean {
    // avoid deprecation warning
    const which = event["which" + ""];
    return which === key;
}

function partition<T>(predicate: (t: T) => boolean, items: T[]): [T[], T[]] {
    const trueItems: T[] = [];
    const falseItems: T[] = [];

    items.forEach((t) => {
        const target = predicate(t) ? trueItems : falseItems;
        target.push(t);
    });

    return [trueItems, falseItems];
}

function removeTrailing(modifier: string): string {
    return modifier.substring(0, modifier.length - 1);
}

function separateRequiredOptionalModifiers(
    modifiers: string[],
): [Modifier[], Modifier[]] {
    const [requiredModifiers, otherModifiers] = partition(
        isRequiredModifier,
        modifiers,
    );

    const optionalModifiers = otherModifiers.map(removeTrailing);
    return [requiredModifiers as Modifier[], optionalModifiers as Modifier[]];
}

const check =
    (keyCode: number, requiredModifiers: Modifier[], optionalModifiers: Modifier[]) =>
    (event: KeyboardEvent): boolean => {
        return (
            checkKey(event, keyCode)
            && checkModifiers(requiredModifiers, optionalModifiers)(event)
        );
    };

function keyToCode(key: string): number {
    return keyCodeLookup[key] || key.toUpperCase().charCodeAt(0);
}

function keyCombinationToCheck(
    keyCombination: string[],
): (event: KeyboardEvent) => boolean {
    const keyCode = keyToCode(keyCombination[keyCombination.length - 1]);
    const [required, optional] = separateRequiredOptionalModifiers(
        keyCombination.slice(0, -1),
    );

    console.log({ keyCode, required, optional });
    return check(keyCode, required, optional);
}

function innerShortcut(
    target: EventTarget | Document,
    lastEvent: KeyboardEvent,
    callback: (event: KeyboardEvent) => void,
    ...checks: ((event: KeyboardEvent) => boolean)[]
): void {
    if (checks.length === 0) {
        return callback(lastEvent);
    }

    const [nextCheck, ...restChecks] = checks;
    const remove = on(document, "keydown", handler, { once: true });

    function handler(event: KeyboardEvent): void {
        if (nextCheck(event)) {
            innerShortcut(target, event, callback, ...restChecks);
        } else if (!checkIfModifierKey(event)) {
            // Any non-modifier key will cancel the shortcut sequence
            remove();
        }
    }
}

export interface RegisterShortcutRestParams {
    target: EventTarget;
    /** There might be no good reason to use `keyup` other
    than to circumvent Qt bugs */
    event: "keydown" | "keyup";
}

const defaultRegisterShortcutRestParams = {
    target: document,
    event: "keydown" as const,
};

export function registerShortcut(
    callback: (event: KeyboardEvent) => void,
    keyCombinationString: string,
    restParams: Partial<RegisterShortcutRestParams> = defaultRegisterShortcutRestParams,
): () => void {
    const {
        target = defaultRegisterShortcutRestParams.target,
        event = defaultRegisterShortcutRestParams.event,
    } = restParams;

    const [check, ...restChecks] = splitKeyCombinationString(keyCombinationString).map(keyCombinationToCheck);

    function handler(event: KeyboardEvent): void {
        if (check(event)) {
            innerShortcut(target, event, callback, ...restChecks);
        }
    }

    return on(target, event, handler);
}

registerPackage("anki/shortcuts", {
    registerShortcut,
    getPlatformString,
});
