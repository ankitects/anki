// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import * as tr from "./i18n";
import { isApplePlatform } from "./platform";

export type Modifier = "Control" | "Alt" | "Shift" | "Meta";

// how modifiers are mapped
const allModifiers: Modifier[] = ["Control", "Alt", "Shift", "Meta"];

const platformModifiers = isApplePlatform()
    ? ["Meta", "Alt", "Shift", "Control"]
    : ["Control", "Alt", "Shift", "OS"];


export const checkModifiers =
    (required: Modifier[], optional: Modifier[] = []) =>
    (event: KeyboardEvent): boolean => {
        return allModifiers.reduce(
            (
                matches: boolean,
                currentModifier: Modifier,
                currentIndex: number
            ): boolean =>
                matches &&
                (optional.includes(currentModifier as Modifier) ||
                    event.getModifierState(platformModifiers[currentIndex]) ===
                        required.includes(currentModifier)),
            true
        );
    };

export const hasModifier = (modifier: Modifier) => (event: KeyboardEvent): boolean => event.getModifierState(platformModifiers[allModifiers.indexOf(modifier)]);

export function isControl(key: string): boolean {
    return key === platformModifiers[0];
}

export function isShift(key: string): boolean {
    return key === platformModifiers[2];
}

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
