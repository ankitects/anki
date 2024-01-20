// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import "../sveltelib/export-runtime";
import "./deck-options-base.scss";

import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";

import { modalsKey, touchDeviceKey } from "../components/context-keys";
import DeckOptionsPage from "./DeckOptionsPage.svelte";
import { DeckOptionsState } from "./lib";

const i18n = setupI18n({
    modules: [
        ModuleName.HELP,
        ModuleName.SCHEDULING,
        ModuleName.ACTIONS,
        ModuleName.DECK_CONFIG,
        ModuleName.KEYBOARD,
        ModuleName.STUDYING,
        ModuleName.DECKS,
    ],
});

export async function setupDeckOptions(did_: number): Promise<DeckOptionsPage> {
    const did = BigInt(did_);
    const [info] = await Promise.all([getDeckConfigsForUpdate({ did }), i18n]);

    checkNightMode();

    const context = new Map();
    context.set(modalsKey, new Map());
    context.set(touchDeviceKey, "ontouchstart" in document.documentElement);

    const state = new DeckOptionsState(BigInt(did), info);
    return new DeckOptionsPage({
        target: document.body,
        props: { state },
        context,
    });
}

import { getDeckConfigsForUpdate } from "@tslib/backend";

import EnumSelectorRow from "../components/EnumSelectorRow.svelte";
import SwitchRow from "../components/SwitchRow.svelte";
import TitledContainer from "../components/TitledContainer.svelte";
import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
import SpinBoxRow from "./SpinBoxRow.svelte";

export const components = {
    TitledContainer,
    SpinBoxRow,
    SpinBoxFloatRow,
    EnumSelectorRow,
    SwitchRow,
};

if (window.location.hash.startsWith("#test")) {
    setupDeckOptions(1);
}
