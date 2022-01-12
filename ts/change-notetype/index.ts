// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import { ChangeNotetypeState, getChangeNotetypeInfo, getNotetypeNames } from "./lib";
import { setupI18n, ModuleName } from "../lib/i18n";
import { checkNightMode } from "../lib/nightmode";

import ChangeNotetypePage from "./ChangeNotetypePage.svelte";
import "./change-notetype-base.css";

export async function setupChangeNotetypePage(
    oldNotetypeId: number,
    newNotetypeId: number,
): Promise<ChangeNotetypePage> {
    const [info, names] = await Promise.all([
        getChangeNotetypeInfo(oldNotetypeId, newNotetypeId),
        getNotetypeNames(),
        setupI18n({
            modules: [
                ModuleName.ACTIONS,
                ModuleName.CHANGE_NOTETYPE,
                ModuleName.KEYBOARD,
            ],
        }),
    ]);

    checkNightMode();

    const state = new ChangeNotetypeState(names, info);
    return new ChangeNotetypePage({
        target: document.body,
        props: { state } as any,
    });
}

// use #testXXXX where XXXX is notetype ID to test
if (window.location.hash.startsWith("#test")) {
    const ntid = parseInt(window.location.hash.substr("#test".length), 10);
    setupChangeNotetypePage(ntid, ntid)
}
