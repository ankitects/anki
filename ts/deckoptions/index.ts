// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import { getDeckOptionsInfo, DeckOptionsState } from "./lib";
import { setupI18n, ModuleName } from "lib/i18n";
import { checkNightMode } from "lib/nightmode";
import DeckOptionsPage from "./DeckOptionsPage.svelte";
import SpinBox from "./SpinBox.svelte";
import SpinBoxFloat from "./SpinBoxFloat.svelte";
import EnumSelector from "./EnumSelector.svelte";
import CheckBox from "./CheckBox.svelte";

import { nightModeKey, modalsKey } from "components/contextKeys";

export async function deckOptions(
    target: HTMLDivElement,
    deckId: number
): Promise<DeckOptionsPage> {
    const [info] = await Promise.all([
        getDeckOptionsInfo(deckId),
        setupI18n({
            modules: [
                ModuleName.SCHEDULING,
                ModuleName.ACTIONS,
                ModuleName.DECK_CONFIG,
            ],
        }),
    ]);

    const nightMode = checkNightMode();
    const context = new Map();
    context.set(nightModeKey, nightMode);

    const modals = new Map();
    context.set(modalsKey, modals);

    const state = new DeckOptionsState(deckId, info);
    return new DeckOptionsPage({
        target,
        props: { state },
        context,
    } as any);
}

export const deckConfigComponents = {
    SpinBox,
    SpinBoxFloat,
    EnumSelector,
    CheckBox,
};
