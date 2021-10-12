// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getCardStats } from "./lib";
import { setupI18n, ModuleName } from "../lib/i18n";
import { checkNightMode } from "../lib/nightmode";

import CardInfoPage from "./CardInfoPage.svelte";

export async function cardInfoPage(
    target: HTMLDivElement,
    cardId: number
): Promise<CardInfoPage> {
    checkNightMode();
    const [stats] = await Promise.all([
        getCardStats(cardId),
        setupI18n({
            modules: [
                ModuleName.CARD_STATS,
                ModuleName.SCHEDULING,
                ModuleName.STATISTICS,
            ],
        }),
    ]);
    return new CardInfoPage({
        target,
        props: { stats },
    });
}
