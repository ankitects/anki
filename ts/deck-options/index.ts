// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import { getDeckOptionsInfo, DeckOptionsState } from "./lib";
import { setupI18n, ModuleName } from "lib/i18n";
import { checkNightMode } from "lib/nightmode";
import DeckOptionsPage from "./DeckOptionsPage.svelte";
import { nightModeKey, touchDeviceKey, modalsKey } from "components/context-keys";

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

    const context = new Map();
    const nightMode = checkNightMode();
    context.set(nightModeKey, nightMode);

    const modals = new Map();
    context.set(modalsKey, modals);

    const touchDevice = "ontouchstart" in document.documentElement;
    context.set(touchDeviceKey, touchDevice);

    const state = new DeckOptionsState(deckId, info);
    return new DeckOptionsPage({
        target,
        props: { state },
        context,
    } as any);
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
