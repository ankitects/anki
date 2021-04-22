// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import * as tr from "./i18n";

const modifiers = ["Control", "Alt", "Shift", "Meta"];

// how modifiers are mapped
const platformModifiers =
    navigator.platform === "MacIntel"
        ? ["Meta", "Alt", "Shift", "Control"]
        : ["Control", "Alt", "Shift", "OS"];

function splitKeyCombinationString(keyCombinationString: string): string[][] {
    return keyCombinationString.split(", ").map((segment) => segment.split("+"));
}

function modifiersToPlatformString(modifiers: string[]): string {
    const displayModifiers =
        navigator.platform === "MacIntel"
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
    return key.startsWith(alphabeticPrefix)
        ? key.slice(alphabeticPrefix.length)
        : key.startsWith(numericPrefix)
        ? key.slice(numericPrefix.length)
        : Object.prototype.hasOwnProperty.call(keyToCharacterMap, key)
        ? keyToCharacterMap[key]
        : key;
}

function toPlatformString(modifiersAndKey: string[]): string {
    return `${modifiersToPlatformString(
        modifiersAndKey.slice(0, -1)
    )}${keyToPlatformString(modifiersAndKey[modifiersAndKey.length - 1])}`;
}

export function getPlatformString(keyCombinationString: string): string {
    return splitKeyCombinationString(keyCombinationString)
        .map(toPlatformString)
        .join(", ");
}

function checkKey(event: KeyboardEvent, key: string): boolean {
    return event.code === key;
}

function checkModifiers(event: KeyboardEvent, activeModifiers: string[]): boolean {
    return modifiers.reduce(
        (matches: boolean, modifier: string, currentIndex: number): boolean =>
            matches &&
            event.getModifierState(platformModifiers[currentIndex]) ===
                activeModifiers.includes(modifier),
        true
    );
}

function check(event: KeyboardEvent, modifiersAndKey: string[]): boolean {
    return (
        checkKey(event, modifiersAndKey[modifiersAndKey.length - 1]) &&
        checkModifiers(event, modifiersAndKey.slice(0, -1))
    );
}

const shortcutTimeoutMs = 400;

function innerShortcut(
    lastEvent: KeyboardEvent,
    callback: (event: KeyboardEvent) => void,
    ...keyCombination: string[][]
): void {
    let interval: number;

    if (keyCombination.length === 0) {
        callback(lastEvent);
    } else {
        const [nextKey, ...restKeys] = keyCombination;

        const handler = (event: KeyboardEvent): void => {
            if (check(event, nextKey)) {
                innerShortcut(event, callback, ...restKeys);
                clearTimeout(interval);
            }
        };

        interval = setTimeout(
            (): void => document.removeEventListener("keydown", handler),
            shortcutTimeoutMs
        );

        document.addEventListener("keydown", handler, { once: true });
    }
}

export function registerShortcut(
    callback: (event: KeyboardEvent) => void,
    keyCombinationString: string
): () => void {
    const keyCombination = splitKeyCombinationString(keyCombinationString);
    const [firstKey, ...restKeys] = keyCombination;

    const handler = (event: KeyboardEvent): void => {
        if (check(event, firstKey)) {
            innerShortcut(event, callback, ...restKeys);
        }
    };

    document.addEventListener("keydown", handler);
    return (): void => document.removeEventListener("keydown", handler);
}
