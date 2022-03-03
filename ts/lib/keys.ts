// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "./ftl";
import { isApplePlatform } from "./platform";

// those are the modifiers that Anki works with
export type Modifier = "Control" | "Alt" | "Shift" | "Meta";
const allModifiers: Modifier[] = ["Control", "Alt", "Shift", "Meta"];

const platformModifiers: string[] = isApplePlatform()
    ? ["Meta", "Alt", "Shift", "Control"]
    : ["Control", "Alt", "Shift", "OS"];

function translateModifierToPlatform(modifier: Modifier): string {
    return platformModifiers[allModifiers.indexOf(modifier)];
}

const GENERAL_KEY = 0;
const NUMPAD_KEY = 3;

export function checkIfInputKey(event: KeyboardEvent): boolean {
    return event.location === GENERAL_KEY || event.location === NUMPAD_KEY;
}

export function keyboardEventIsPrintableKey(event: KeyboardEvent): boolean {
    return event.key.length === 1;
}

export const checkModifiers =
    (required: Modifier[], optional: Modifier[] = []) =>
    (event: KeyboardEvent): boolean => {
        return allModifiers.reduce(
            (
                matches: boolean,
                currentModifier: Modifier,
                currentIndex: number,
            ): boolean =>
                matches &&
                (optional.includes(currentModifier as Modifier) ||
                    event.getModifierState(platformModifiers[currentIndex]) ===
                        required.includes(currentModifier)),
            true,
        );
    };

const modifierPressed =
    (modifier: Modifier) =>
    (event: MouseEvent | KeyboardEvent): boolean => {
        const translated = translateModifierToPlatform(modifier);
        const state = event.getModifierState(translated);
        return event.type === "keyup"
            ? state && (event as KeyboardEvent).key !== translated
            : state;
    };

export const controlPressed = modifierPressed("Control");
export const shiftPressed = modifierPressed("Shift");
export const altPressed = modifierPressed("Alt");

export function modifiersToPlatformString(modifiers: string[]): string {
    const displayModifiers = isApplePlatform()
        ? ["^", "⌥", "⇧", "⌘"]
        : [`${tr.keyboardCtrl()}+`, "Alt+", `${tr.keyboardShift()}+`, "Win+"];

    let result = "";

    for (const modifier of modifiers) {
        result += displayModifiers[platformModifiers.indexOf(modifier)];
    }

    return result;
}

export function keyToPlatformString(key: string): string {
    switch (key) {
        case "Backspace":
            return "⌫";
        case "Delete":
            return "⌦";
        case "Escape":
            return "⎋";

        default:
            return key;
    }
}
