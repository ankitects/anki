// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { FluentBundle, setBundles } from "@generated/ftl";
import { expect, test } from "vitest";

import { usesArabicScript } from "./utils";

function setLanguage(lang: string): void {
    setBundles([new FluentBundle([lang, "en-US"])]);
}

test("Arabic-script languages mirror the question mark", () => {
    for (const lang of ["ar", "ar-SA", "fa", "fa-IR"]) {
        setLanguage(lang);
        expect(usesArabicScript()).toBe(true);
    }
});

test("Hebrew is RTL but keeps the Latin question mark", () => {
    for (const lang of ["he", "en", "en-US"]) {
        setLanguage(lang);
        expect(usesArabicScript()).toBe(false);
    }
});
