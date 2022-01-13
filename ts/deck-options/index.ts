// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import "../sveltelib/export-runtime";

import { getDeckOptionsInfo, DeckOptionsState } from "./lib";
import { setupI18n, ModuleName } from "../lib/i18n";
import { checkNightMode } from "../lib/nightmode";
import { touchDeviceKey, modalsKey } from "../components/context-keys";

import DeckOptionsPage from "./DeckOptionsPage.svelte";
import "./deck-options-base.css";

const i18n = setupI18n({
    modules: [
        ModuleName.SCHEDULING,
        ModuleName.ACTIONS,
        ModuleName.DECK_CONFIG,
        ModuleName.KEYBOARD,
    ],
});

export async function setupDeckOptions(deckId: number): Promise<DeckOptionsPage> {
    const [info] = await Promise.all([getDeckOptionsInfo(deckId), i18n]);

    checkNightMode();

    const context = new Map();
    context.set(modalsKey, new Map());
    context.set(touchDeviceKey, "ontouchstart" in document.documentElement);

    const state = new DeckOptionsState(deckId, info);
    return new DeckOptionsPage({
        target: document.body,
        props: { state },
        context,
    });
}

import TitledContainer from "./TitledContainer.svelte";
import SpinBoxRow from "./SpinBoxRow.svelte";
import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
import EnumSelectorRow from "./EnumSelectorRow.svelte";
import SwitchRow from "./SwitchRow.svelte";

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
