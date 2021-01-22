// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import pb from "./fluent_proto";
import "intl-pluralrules";
import { FluentBundle, FluentResource, FluentNumber } from "@fluent/bundle/compat";

type RecordVal = number | string | FluentNumber;

function formatNumbers(args?: Record<string, RecordVal>): void {
    if (!args) {
        return;
    }
    for (const key of Object.keys(args)) {
        if (typeof args[key] === "number") {
            args[key] = new FluentNumber(args[key] as number, {
                maximumFractionDigits: 2,
            });
        }
    }
}

export class I18n {
    bundles: FluentBundle[] = [];
    langs: string[] = [];
    TR = pb.FluentProto.FluentString;

    tr(id: pb.FluentProto.FluentString, args?: Record<string, RecordVal>): string {
        formatNumbers(args);
        const key = this.keyName(id);
        for (const bundle of this.bundles) {
            const msg = bundle.getMessage(key);
            if (msg && msg.value) {
                return bundle.formatPattern(msg.value, args);
            }
        }
        return `missing key: ${key}`;
    }

    supportsVerticalText(): boolean {
        const firstLang = this.bundles[0].locales[0];
        return (
            firstLang.startsWith("ja") ||
            firstLang.startsWith("zh") ||
            firstLang.startsWith("ko")
        );
    }

    direction(): string {
        const firstLang = this.bundles[0].locales[0];
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

    weekdayLabel(n: number): string {
        const firstLang = this.bundles[0].locales[0];
        return new Date(86_400_000 * (3 + n)).toLocaleDateString(firstLang, {
            weekday: "narrow",
        });
    }

    private keyName(msg: pb.FluentProto.FluentString): string {
        return this.TR[msg].toLowerCase().replace(/_/g, "-");
    }
}

export async function setupI18n(): Promise<I18n> {
    const i18n = new I18n();

    const resp = await fetch("/_anki/i18nResources", { method: "POST" });
    if (!resp.ok) {
        throw Error(`unexpected reply: ${resp.statusText}`);
    }
    const json = await resp.json();

    for (const i in json.resources) {
        const text = json.resources[i];
        const lang = json.langs[i];
        const bundle = new FluentBundle([lang, "en-US"]);
        const resource = new FluentResource(text);
        bundle.addResource(resource);
        i18n.bundles.push(bundle);
    }
    i18n.langs = json.langs;

    return i18n;
}
