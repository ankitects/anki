// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import "../sveltelib/export-runtime";
import "./deck-options-base.css";

import { modalsKey, touchDeviceKey } from "../components/context-keys";
import { ModuleName, setupI18n } from "../lib/i18n";
import { checkNightMode } from "../lib/nightmode";
import { deckConfig, Decks } from "../lib/proto";
import DeckOptionsPage from "./DeckOptionsPage.svelte";
import { DeckOptionsState } from "./lib";

const i18n = setupI18n({
    modules: [
        ModuleName.SCHEDULING,
        ModuleName.ACTIONS,
        ModuleName.DECK_CONFIG,
        ModuleName.KEYBOARD,
    ],
});

export async function setupDeckOptions(did: number): Promise<DeckOptionsPage> {
    const [info] = await Promise.all([
        deckConfig.getDeckConfigsForUpdate(Decks.DeckId.create({ did })),
        i18n,
    ]);

    checkNightMode();

    const context = new Map();
    context.set(modalsKey, new Map());
    context.set(touchDeviceKey, "ontouchstart" in document.documentElement);

    const state = new DeckOptionsState(did, info);
    return new DeckOptionsPage({
        target: document.body,
        props: { state },
        context,
    });
}

import EnumSelectorRow from "./EnumSelectorRow.svelte";
import SpinBoxFloatRow from "./SpinBoxFloatRow.svelte";
import SpinBoxRow from "./SpinBoxRow.svelte";
import SwitchRow from "./SwitchRow.svelte";
import TitledContainer from "./TitledContainer.svelte";

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
