// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { FluentNumber } from "@fluent/bundle";
import type { FluentBundle } from "@fluent/bundle";
type RecordVal = number | string | FluentNumber;

let bundles: FluentBundle[] = [];

export function setBundles(...newBundles: FluentBundle[]): void {
    bundles.splice(0, bundles.length, ...newBundles);
}

export function firstLanguage(): string {
    return bundles[0].locales[0];
}

function formatNumbers(args: Record<string, RecordVal>): void {
    for (const key of Object.keys(args)) {
        if (typeof args[key] === "number") {
            args[key] = new FluentNumber(args[key] as number, {
                maximumFractionDigits: 2,
            });
        }
    }
}

export function translate(key: string, args?: Record<string, RecordVal>): string {
    if (args) {
        formatNumbers(args);
    }

    for (const bundle of bundles) {
        const msg = bundle.getMessage(key);
        if (msg && msg.value) {
            return bundle.formatPattern(msg.value, args);
        }
    }
    return `missing key: ${key}`;
}
