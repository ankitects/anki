// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "./i18n";
import { isApplePlatform } from "./platform";

export type Modifier = "Control" | "Alt" | "Shift" | "Meta";

// how modifiers are mapped
const platformModifiers = isApplePlatform()
    ? ["Meta", "Alt", "Shift", "Control"]
    : ["Control", "Alt", "Shift", "OS"];

function modifiersToPlatformString(modifiers: string[]): string {
    const displayModifiers = isApplePlatform()
        ? ["^", "⌥", "⇧", "⌘"]
        : [`${tr.keyboardCtrl()}+`, "Alt+", `${tr.keyboardShift()}+`, "Win+"];

    let result = "";

    for (const modifier of modifiers) {
        result += displayModifiers[platformModifiers.indexOf(modifier)];
    }

    return result;
}

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
};

function isRequiredModifier(modifier: string): boolean {
    return !modifier.endsWith("?");
}

function splitKeyCombinationString(keyCombinationString: string): string[][] {
    return keyCombinationString.split(", ").map((segment) => segment.split("+"));
}

function toPlatformString(keyCombination: string[]): string {
    return (
        modifiersToPlatformString(
            keyCombination.slice(0, -1).filter(isRequiredModifier)
        ) + keyCombination[keyCombination.length - 1]
    );
}

export function getPlatformString(keyCombinationString: string): string {
    return splitKeyCombinationString(keyCombinationString)
        .map(toPlatformString)
        .join(", ");
}

function checkKey(event: KeyboardEvent, key: number): boolean {
    return event.which === key;
}

const allModifiers: Modifier[] = ["Control", "Alt", "Shift", "Meta"];

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

function checkModifiers(event: KeyboardEvent, modifiers: string[]): boolean {
    const [requiredModifiers, otherModifiers] = partition(
        isRequiredModifier,
        modifiers
    );

    const optionalModifiers = otherModifiers.map(removeTrailing);

    return allModifiers.reduce(
        (matches: boolean, currentModifier: string, currentIndex: number): boolean =>
            matches &&
            (optionalModifiers.includes(currentModifier as Modifier) ||
                event.getModifierState(platformModifiers[currentIndex]) ===
                    requiredModifiers.includes(currentModifier)),
        true
    );
}

const check =
    (keyCode: number, modifiers: string[]) =>
    (event: KeyboardEvent): boolean => {
        return checkKey(event, keyCode) && checkModifiers(event, modifiers);
    };

function keyToCode(key: string): number {
    return keyCodeLookup[key] || key.toUpperCase().charCodeAt(0);
}

function keyCombinationToCheck(
    keyCombination: string[]
): (event: KeyboardEvent) => boolean {
    const keyCode = keyToCode(keyCombination[keyCombination.length - 1]);
    const modifiers = keyCombination.slice(0, -1);

    return check(keyCode, modifiers);
}

const GENERAL_KEY = 0;
const NUMPAD_KEY = 3;

function innerShortcut(
    lastEvent: KeyboardEvent,
    callback: (event: KeyboardEvent) => void,
    ...checks: ((event: KeyboardEvent) => boolean)[]
): void {
    let interval: number;

    if (checks.length === 0) {
        callback(lastEvent);
    } else {
        const [nextCheck, ...restChecks] = checks;
        const handler = (event: KeyboardEvent): void => {
            if (nextCheck(event)) {
                innerShortcut(event, callback, ...restChecks);
                clearTimeout(interval);
            } else if (
                event.location === GENERAL_KEY ||
                event.location === NUMPAD_KEY
            ) {
                // Any non-modifier key will cancel the shortcut sequence
                document.removeEventListener("keydown", handler);
            }
        };

        document.addEventListener("keydown", handler, { once: true });
    }
}

export function registerShortcut(
    callback: (event: KeyboardEvent) => void,
    keyCombinationString: string
): () => void {
    const [check, ...restChecks] =
        splitKeyCombinationString(keyCombinationString).map(keyCombinationToCheck);

    const handler = (event: KeyboardEvent): void => {
        if (check(event)) {
            innerShortcut(event, callback, ...restChecks);
        }
    };

    document.addEventListener("keydown", handler);
    return (): void => document.removeEventListener("keydown", handler);
}
