// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import * as tr from "./i18n";

export type Modifier = "Control" | "Alt" | "Shift" | "Meta";

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

function isRequiredModifier(modifier: string): boolean {
    return !modifier.endsWith("?");
}

function toPlatformString(modifiersAndKey: string[]): string {
    return (
        modifiersToPlatformString(
            modifiersAndKey.slice(0, -1).filter(isRequiredModifier)
        ) + capitalize(keyToPlatformString(modifiersAndKey[modifiersAndKey.length - 1]))
    );
}

export function getPlatformString(keyCombination: string[][]): string {
    return keyCombination.map(toPlatformString).join(", ");
}

function checkKey(
    getProperty: (event: KeyboardEvent) => string,
    event: KeyboardEvent,
    key: string
): boolean {
    return getProperty(event) === key;
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

function check(
    getProperty: (event: KeyboardEvent) => string,
    event: KeyboardEvent,
    modifiersAndKey: string[]
): boolean {
    return (
        checkModifiers(event, modifiersAndKey.slice(0, -1)) &&
        checkKey(getProperty, event, modifiersAndKey[modifiersAndKey.length - 1])
    );
}

const GENERAL_KEY = 0;
const NUMPAD_KEY = 3;

function innerShortcut(
    lastEvent: KeyboardEvent,
    callback: (event: KeyboardEvent) => void,
    getProperty: (event: KeyboardEvent) => string,
    ...keyCombination: string[][]
): void {
    let interval: number;

    if (keyCombination.length === 0) {
        callback(lastEvent);
    } else {
        const [nextKey, ...restKeys] = keyCombination;

        const handler = (event: KeyboardEvent): void => {
            if (check(getProperty, event, nextKey)) {
                innerShortcut(event, callback, getProperty, ...restKeys);
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

function byKey(event: KeyboardEvent): string {
    return event.key;
}

function byCode(event: KeyboardEvent): string {
    return event.code;
}

export function registerShortcut(
    callback: (event: KeyboardEvent) => void,
    keyCombination: string[][],
    useCode = false
): () => void {
    const [firstKey, ...restKeys] = keyCombination;
    const getProperty = useCode ? byCode : byKey;

    const handler = (event: KeyboardEvent): void => {
        if (check(getProperty, event, firstKey)) {
            innerShortcut(event, callback, getProperty, ...restKeys);
        }
    };

    document.addEventListener("keydown", handler);
    return (): void => document.removeEventListener("keydown", handler);
}
