// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import pb from "./backend/proto";
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
    TR = pb.BackendProto.FluentString;

    tr(id: pb.BackendProto.FluentString, args?: Record<string, RecordVal>): string {
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

    private keyName(msg: pb.BackendProto.FluentString): string {
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

    for (const resourceText of json.resources) {
        const bundle = new FluentBundle(json.langs);
        const resource = new FluentResource(resourceText);
        bundle.addResource(resource);
        i18n.bundles.push(bundle);
    }

    return i18n;
}
