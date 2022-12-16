// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./change-notetype-base.scss";

import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";
import { empty, Notetypes, notetypes } from "@tslib/proto";

import ChangeNotetypePage from "./ChangeNotetypePage.svelte";
import { ChangeNotetypeState } from "./lib";

const notetypeNames = notetypes.getNotetypeNames(empty);
const i18n = setupI18n({
    modules: [ModuleName.ACTIONS, ModuleName.CHANGE_NOTETYPE, ModuleName.KEYBOARD],
});

export async function setupChangeNotetypePage(
    oldNotetypeId: number,
    newNotetypeId: number,
): Promise<ChangeNotetypePage> {
    const changeNotetypeInfo = notetypes.getChangeNotetypeInfo(
        Notetypes.GetChangeNotetypeInfoRequest.create({
            oldNotetypeId,
            newNotetypeId,
        }),
    );
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
    const ntid = parseInt(window.location.hash.substring("#test".length), 10);
    setupChangeNotetypePage(ntid, ntid);
}
