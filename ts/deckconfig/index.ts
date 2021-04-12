// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getDeckConfigInfo, stateFromUpdateData } from "./lib";
import { setupI18n, ModuleName } from "anki/i18n";
import { checkNightMode } from "anki/nightmode";
import DeckConfigPage from "./DeckConfigPage.svelte";

export async function deckConfig(
    target: HTMLDivElement,
    deckId: number
): Promise<void> {
    checkNightMode();
    await setupI18n({
        modules: [ModuleName.SCHEDULING, ModuleName.ACTIONS, ModuleName.DECK_CONFIG],
    });
    const info = await getDeckConfigInfo(deckId);
    const state = stateFromUpdateData(info);
    new DeckConfigPage({
        target,
        props: { state },
    });
}
