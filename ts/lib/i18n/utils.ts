// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "intl-pluralrules";

import { FluentBundle, FluentResource } from "@fluent/bundle";

import { I18n, i18n } from "../proto";
import { firstLanguage, setBundles } from "./bundles";
import type { ModuleName } from "./modules";

export function supportsVerticalText(): boolean {
    const firstLang = firstLanguage();
    return (
        firstLang.startsWith("ja")
        || firstLang.startsWith("zh")
        || firstLang.startsWith("ko")
    );
}

export function direction(): string {
    const firstLang = firstLanguage();
    if (
        firstLang.startsWith("ar")
        || firstLang.startsWith("he")
        || firstLang.startsWith("fa")
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

export function localizedDate(
    date: Date,
    options?: Intl.DateTimeFormatOptions,
): string {
    return date.toLocaleDateString(langs, options);
}

export function localizedNumber(n: number, precision = 2): string {
    const round = Math.pow(10, precision);
    const rounded = Math.round(n * round) / round;
    return rounded.toLocaleString(langs);
}

export function localeCompare(
    first: string,
    second: string,
    options?: Intl.CollatorOptions,
): number {
    return first.localeCompare(second, langs, options);
}

/// Treat text like HTML, merging multiple spaces and converting
/// newlines to spaces.
export function withCollapsedWhitespace(s: string): string {
    return s.replace(/\s+/g, " ");
}

export function withoutUnicodeIsolation(s: string): string {
    return s.replace(/[\u2068-\u2069]+/g, "");
}

export async function setupI18n(args: { modules: ModuleName[] }): Promise<void> {
    const resources = await i18n.i18nResources(I18n.I18nResourcesRequest.create(args));
    const json = JSON.parse(new TextDecoder().decode(resources.json));

    const newBundles: FluentBundle[] = [];
    for (const res in json.resources) {
        const text = json.resources[res];
        const lang = json.langs[res];
        const bundle = new FluentBundle([lang, "en-US"]);
        const resource = new FluentResource(text);
        bundle.addResource(resource);
        newBundles.push(bundle);
    }

    setBundles(newBundles);
    langs = json.langs;

    document.dir = direction();
}
