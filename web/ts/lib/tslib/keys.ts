// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "@generated/ftl";

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

export function checkIfModifierKey(event: KeyboardEvent): boolean {
    // At least the web view on Desktop Anki gives out the wrong values for
    // `event.location`, which is why we do it like this.
    let isInputKey = false;

    for (const modifier of allModifiers) {
        isInputKey ||= event.code.startsWith(modifier);
    }

    return isInputKey;
}

export function keyboardEventIsPrintableKey(event: KeyboardEvent): boolean {
    return event.key.length === 1;
}

export const checkModifiers = (required: Modifier[], optional: Modifier[] = []) => (event: KeyboardEvent): boolean => {
    return allModifiers.reduce(
        (
            matches: boolean,
            currentModifier: Modifier,
            currentIndex: number,
        ): boolean =>
            matches
            && (optional.includes(currentModifier as Modifier)
                || event.getModifierState(platformModifiers[currentIndex])
                    === required.includes(currentModifier)),
        true,
    );
};

const modifierPressed = (modifier: Modifier) => (event: MouseEvent | KeyboardEvent): boolean => {
    const translated = translateModifierToPlatform(modifier);
    const state = event.getModifierState(translated);
    return event.type === "keyup"
        ? state && (event as KeyboardEvent).key !== translated
        : state;
};

export const controlPressed = modifierPressed("Control");
export const shiftPressed = modifierPressed("Shift");
export const altPressed = modifierPressed("Alt");
export const metaPressed = modifierPressed("Meta");

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

export function isArrowLeft(event: KeyboardEvent): boolean {
    if (event.key === "ArrowLeft") {
        return true;
    }

    return isApplePlatform() && metaPressed(event) && event.code === "KeyB";
}

export function isArrowRight(event: KeyboardEvent): boolean {
    if (event.key === "ArrowRight") {
        return true;
    }

    return isApplePlatform() && metaPressed(event) && event.code === "KeyF";
}

export function isArrowUp(event: KeyboardEvent): boolean {
    if (event.key === "ArrowUp") {
        return true;
    }

    return isApplePlatform() && metaPressed(event) && event.code === "KeyP";
}

export function isArrowDown(event: KeyboardEvent): boolean {
    if (event.key === "ArrowDown") {
        return true;
    }

    return isApplePlatform() && metaPressed(event) && event.code === "KeyN";
}

export function onEnterOrSpace(callback: () => void): (event: KeyboardEvent) => void {
    return (event: KeyboardEvent) => {
        switch (event.code) {
            case "Enter":
            case "Space":
                callback();
                break;
        }
    };
}
