// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// An i18n singleton and setupI18n is re-exported via the generated i18n.ts file,
// so you should not need to access this file directly.

import "intl-pluralrules";
import { FluentBundle, FluentResource, FluentNumber } from "@fluent/bundle";
import type { ModuleName } from "./i18n-modules";

type RecordVal = number | string | FluentNumber;

function formatNumbers(args: Record<string, RecordVal>): void {
    for (const key of Object.keys(args)) {
        if (typeof args[key] === "number") {
            args[key] = new FluentNumber(args[key] as number, {
                maximumFractionDigits: 2,
            });
        }
    }
}

let bundles: FluentBundle[] = [];

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

export function supportsVerticalText(): boolean {
    const firstLang = bundles[0].locales[0];
    return (
        firstLang.startsWith("ja") ||
        firstLang.startsWith("zh") ||
        firstLang.startsWith("ko")
    );
}

export function direction(): string {
    const firstLang = bundles[0].locales[0];
    if (
        firstLang.startsWith("ar") ||
        firstLang.startsWith("he") ||
        firstLang.startsWith("fa")
    ) {
        return "rtl";
    } else {
        return "ltr";
    }
}

export function weekdayLabel(n: number): string {
    const firstLang = bundles[0].locales[0];
    const now = new Date();
    const daysFromToday = -now.getDay() + n;
    const desiredDay = new Date(now.getTime() + daysFromToday * 86_400_000);
    return desiredDay.toLocaleDateString(firstLang, {
        weekday: "narrow",
    });
}

let langs: string[] = [];

export function toLocaleString(
    date: Date,
    options?: Intl.DateTimeFormatOptions
): string {
    return date.toLocaleDateString(langs, options);
}

export function localeCompare(
    first: string,
    second: string,
    options?: Intl.CollatorOptions
): number {
    return first.localeCompare(second, langs, options);
}

export async function setupI18n(args: { modules: ModuleName[] }): Promise<void> {
    const resp = await fetch("/_anki/i18nResources", {
        method: "POST",
        body: JSON.stringify(args),
    });
    if (!resp.ok) {
        throw Error(`unexpected reply: ${resp.statusText}`);
    }
    const json = await resp.json();

    bundles = [];
    for (const i in json.resources) {
        const text = json.resources[i];
        const lang = json.langs[i];
        const bundle = new FluentBundle([lang, "en-US"]);
        const resource = new FluentResource(text);
        bundle.addResource(resource);
        bundles.push(bundle);
    }

    langs = json.langs;
}

export { ModuleName } from "./i18n-modules";
export * as tr from "./i18n-translate";
