// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "intl-pluralrules";
import { FluentBundle, FluentResource } from "@fluent/bundle";

import { firstLanguage, setBundles } from "./bundles";
import type { ModuleName } from "./modules";

export function supportsVerticalText(): boolean {
    const firstLang = firstLanguage();
    return (
        firstLang.startsWith("ja") ||
        firstLang.startsWith("zh") ||
        firstLang.startsWith("ko")
    );
}

export function direction(): string {
    const firstLang = firstLanguage();
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
    const firstLang = firstLanguage();
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

    const newBundles: FluentBundle[] = [];
    for (const i in json.resources) {
        const text = json.resources[i];
        const lang = json.langs[i];
        const bundle = new FluentBundle([lang, "en-US"]);
        const resource = new FluentResource(text);
        bundle.addResource(resource);
        newBundles.push(bundle);
    }

    setBundles(...newBundles);
    langs.splice(0, langs.length, ...json.langs);
}
