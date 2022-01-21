// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { setupI18n, ModuleName } from "../lib/i18n";
import { notetypes, empty } from "../lib/proto";
import { checkNightMode } from "../lib/nightmode";
import { ChangeNotetypeState } from "./lib";

import ChangeNotetypePage from "./ChangeNotetypePage.svelte";
import "./change-notetype-base.css";

const notetypeNames = notetypes.getNotetypeNames(empty);
const i18n = setupI18n({
    modules: [ModuleName.ACTIONS, ModuleName.CHANGE_NOTETYPE, ModuleName.KEYBOARD],
});

export async function setupChangeNotetypePage(
    oldNotetypeId: number,
    newNotetypeId: number,
): Promise<ChangeNotetypePage> {
    const changeNotetypeInfo = notetypes.getChangeNotetypeInfo({
        oldNotetypeId,
        newNotetypeId,
    });
    const [names, info] = await Promise.all([notetypeNames, changeNotetypeInfo, i18n]);

    checkNightMode();

    const state = new ChangeNotetypeState(names, info);
    return new ChangeNotetypePage({
        target: document.body,
        props: { state },
    });
}

// use #testXXXX where XXXX is notetype ID to test
if (window.location.hash.startsWith("#test")) {
    const ntid = parseInt(window.location.hash.substr("#test".length), 10);
    setupChangeNotetypePage(ntid, ntid);
}
