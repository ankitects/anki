// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { Callback } from "@tslib/typing";
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
};

const nativeShortcuts: { sequenceStart: string; remove: Callback }[] = [];
const externalShortcuts = new Map<
    { keyCombinationString: string; callback: (event: KeyboardEvent) => void },
    Callback
>();

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

    return check(keyCode, required, optional);
}

function sequenceStart(keyCombinationString: string): string {
    return keyCombinationString.split(",")[0];
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

/**
 * Removes all native shortcuts that conflict with the given key combination.
 * @example
 * The keyCombinationString "Control+T" conflicts with:
 * - "Control+T"
 * but also with combined shortcuts like:
 * - "Control+T, E"
 */
function removeConflictingShortcuts(keyCombinationString: string) {
    let i = nativeShortcuts.length;
    while (i--) {
        const shortcut = nativeShortcuts[i];
        if (shortcut.sequenceStart === sequenceStart(keyCombinationString)) {
            shortcut.remove();
            nativeShortcuts.splice(i, 1);
        }
    }
}

export interface RegisterShortcutRestParams {
    target: EventTarget;
    /// There might be no good reason to use `keyup` other
    /// than to circumvent Qt bugs
    event: "keydown" | "keyup";
    override: boolean;
}

const defaultRegisterShortcutRestParams = {
    target: document,
    event: "keydown" as const,
};

function registerShortcutInner(
    callback: (event: KeyboardEvent) => void,
    keyCombinationString: string,
    restParams: Partial<RegisterShortcutRestParams>,
): Callback {
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

/**
 * Used by native Anki components
 */
export function registerShortcut(
    callback: (event: KeyboardEvent) => void,
    keyCombinationString: string,
    restParams: Partial<RegisterShortcutRestParams> = defaultRegisterShortcutRestParams,
): Callback {
    /**
     * Native shortcuts may be registered at a later time, e.g. when the Cloze notetype is loaded,
     * so we iterate external shortcuts and prevent registration in case of conflict.
     */
    for (const shortcut of externalShortcuts.keys()) {
        if (
            sequenceStart(shortcut.keyCombinationString)
                === sequenceStart(keyCombinationString)
        ) {
            return () => {};
        }
    }
    const remove = registerShortcutInner(callback, keyCombinationString, restParams);

    nativeShortcuts.push({
        sequenceStart: sequenceStart(keyCombinationString),
        remove,
    });

    return remove;
}

// Expose wrapper function which overrides conflicting native shortcuts
registerPackage("anki/shortcuts", {
    registerShortcut: (
        callback: (event: KeyboardEvent) => void,
        keyCombinationString: string,
        restParams: Partial<RegisterShortcutRestParams> = defaultRegisterShortcutRestParams,
    ) => {
        removeConflictingShortcuts(keyCombinationString);

        const key = { keyCombinationString, callback };
        const remove = registerShortcutInner(
            callback,
            keyCombinationString,
            restParams,
        );

        externalShortcuts.set(key, remove);

        return () => {
            externalShortcuts.get(key)?.();
            externalShortcuts.delete(key);
        };
    },
    getPlatformString,
});
