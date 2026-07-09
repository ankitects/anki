// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { FluentBundle, FluentVariable } from "@fluent/bundle";
import { FluentNumber } from "@fluent/bundle";

let bundles: FluentBundle[] = [];

export function setBundles(newBundles: FluentBundle[]): void {
    bundles = newBundles;
}

export function firstLanguage(): string {
    return bundles[0].locales[0];
}

export function translate(key: string, args: Record<string, FluentVariable> = {}) {
    return getMessage(key, args) ?? `missing key: ${key}`;
}

function toFluentNumber(num: number): FluentNumber {
    return new FluentNumber(num, {
        maximumFractionDigits: 2,
    });
}

function formatArgs(
    args: Record<string, FluentVariable>,
): Record<string, FluentVariable> {
    const entries: [string, FluentVariable][] = Object.entries(args).map(
        ([key, value]) => {
            return [
                key,
                typeof value === "number" ? toFluentNumber(value) : value,
            ];
        },
    );
    const out: Record<string, FluentVariable> = {};
    for (const [key, value] of entries) {
        out[key] = value;
    }
    return out;
}

function getMessage(
    key: string,
    args: Record<string, FluentVariable> = {},
): string | null {
    for (const bundle of bundles) {
        const msg = bundle.getMessage(key);
        if (msg && msg.value) {
            const errors = [];
            const formatted = bundle.formatPattern(msg.value, formatArgs(args), errors);
            if (errors.length) {
                console.warn(
                    `Found issues in translation string ${key} with locales ${bundle.locales}:`,
                );
                for (const error of errors) {
                    console.error(error);
                }
            }
            return formatted;
        }
    }

    return null;
}
