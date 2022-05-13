// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./fields-base.css";

import { ModuleName, setupI18n } from "../lib/i18n";
import { checkNightMode } from "../lib/nightmode";
import FieldsPage from "./FieldsPage.svelte";

const i18n = setupI18n({
    modules: [ModuleName.ACTIONS, ModuleName.CHANGE_NOTETYPE, ModuleName.KEYBOARD],
});

async function setupFieldsPage(): Promise<FieldsPage> {
    checkNightMode();

    await i18n;
    return new FieldsPage({
        target: document.body,
    });
}

const pagePromise = setupFieldsPage();

export async function initializeFields(notetypeId: number): Promise<void> {
    const page = await pagePromise;
    page.$set({ notetypeId });
}
