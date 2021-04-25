// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getDeckOptionsInfo, DeckOptionsState } from "./lib";
import { setupI18n, ModuleName } from "lib/i18n";
import { checkNightMode } from "lib/nightmode";
import DeckOptionsPage from "./DeckOptionsPage.svelte";
import SpinBox from "./SpinBox.svelte";
import SpinBoxFloat from "./SpinBoxFloat.svelte";
import EnumSelector from "./EnumSelector.svelte";
import CheckBox from "./CheckBox.svelte";

export async function deckOptions(
    target: HTMLDivElement,
    deckId: number
): Promise<DeckOptionsPage> {
    checkNightMode();
    await setupI18n({
        modules: [ModuleName.SCHEDULING, ModuleName.ACTIONS, ModuleName.DECK_CONFIG],
    });
    const info = await getDeckOptionsInfo(deckId);
    const state = new DeckOptionsState(deckId, info);
    return new DeckOptionsPage({
        target,
        props: { state },
    });
}

export const deckConfigComponents = {
    SpinBox,
    SpinBoxFloat,
    EnumSelector,
    CheckBox,
};
