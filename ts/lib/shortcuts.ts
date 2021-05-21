// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import * as tr from "./i18n";

export type Modifier = "Control" | "Alt" | "Shift" | "Meta";

const modifiers: Modifier[] = ["Control", "Alt", "Shift", "Meta"];

function isApplePlatform(): boolean {
    return (
        window.navigator.platform.startsWith("Mac") ||
        window.navigator.platform.startsWith("iP")
    );
}

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

const alphabeticPrefix = "Key";
const numericPrefix = "Digit";
const keyToCharacterMap = {
    Backslash: "\\",
    Backquote: "`",
    BracketLeft: "[",
    BrackerRight: "]",
    Quote: "'",
    Semicolon: ";",
    Minus: "-",
    Equal: "=",
    Comma: ",",
    Period: ".",
    Slash: "/",
};

function keyToPlatformString(key: string): string {
    if (key.startsWith(alphabeticPrefix)) {
        return key.slice(alphabeticPrefix.length);
    } else if (key.startsWith(numericPrefix)) {
        return key.slice(numericPrefix.length);
    } else if (Object.prototype.hasOwnProperty.call(keyToCharacterMap, key)) {
        return keyToCharacterMap[key];
    } else {
        return key;
    }
}

function capitalize(key: string): string {
    return key[0].toLocaleUpperCase() + key.slice(1);
}

function toPlatformString(modifiersAndKey: string[]): string {
    return (
        modifiersToPlatformString(modifiersAndKey.slice(0, -1)) +
        capitalize(keyToPlatformString(modifiersAndKey[modifiersAndKey.length - 1]))
    );
}

export function getPlatformString(keyCombination: string[][]): string {
    return keyCombination.map(toPlatformString).join(", ");
}

function checkKey(event: KeyboardEvent, key: string): boolean {
    return event.key === key;
}

function checkModifiers(
    event: KeyboardEvent,
    optionalModifiers: Modifier[],
    activeModifiers: string[]
): boolean {
    return modifiers.reduce(
        (matches: boolean, modifier: string, currentIndex: number): boolean =>
            matches &&
            (optionalModifiers.includes(modifier as Modifier) ||
                event.getModifierState(platformModifiers[currentIndex]) ===
                    activeModifiers.includes(modifier)),
        true
    );
}

function check(
    event: KeyboardEvent,
    optionalModifiers: Modifier[],
    modifiersAndKey: string[]
): boolean {
    return (
        checkKey(event, modifiersAndKey[modifiersAndKey.length - 1]) &&
        checkModifiers(event, optionalModifiers, modifiersAndKey.slice(0, -1))
    );
}

const GENERAL_KEY = 0;
const NUMPAD_KEY = 3;

function innerShortcut(
    lastEvent: KeyboardEvent,
    callback: (event: KeyboardEvent) => void,
    optionalModifiers: Modifier[],
    ...keyCombination: string[][]
): void {
    let interval: number;

    if (keyCombination.length === 0) {
        callback(lastEvent);
    } else {
        const [nextKey, ...restKeys] = keyCombination;

        const handler = (event: KeyboardEvent): void => {
            if (check(event, optionalModifiers, nextKey)) {
                innerShortcut(event, callback, optionalModifiers, ...restKeys);
                clearTimeout(interval);
            } else if (event.location === GENERAL_KEY || event.location === NUMPAD_KEY) {
                // Any non-modifier key will cancel the shortcut sequence
                document.removeEventListener("keydown", handler);
            }
        };

        document.addEventListener("keydown", handler, { once: true });
    }
}

export function registerShortcut(
    callback: (event: KeyboardEvent) => void,
    keyCombination: string[][],
    optionalModifiers: Modifier[] = []
): () => void {
    const [firstKey, ...restKeys] = keyCombination;

    const handler = (event: KeyboardEvent): void => {
        if (check(event, optionalModifiers, firstKey)) {
            innerShortcut(event, callback, optionalModifiers, ...restKeys);
        }
    };

    document.addEventListener("keydown", handler);
    return (): void => document.removeEventListener("keydown", handler);
}
