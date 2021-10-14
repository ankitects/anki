// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getCardStats } from "./lib";
import { setupI18n, ModuleName } from "../lib/i18n";
import { checkNightMode } from "../lib/nightmode";

import CardInfo from "./CardInfo.svelte";

export async function cardInfo(
    target: HTMLDivElement,
    cardId: number,
    includeRevlog: boolean
): Promise<CardInfo> {
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
    if (!includeRevlog) {
        stats.revlog = [];
    }
    return new CardInfo({
        target,
        props: { stats },
    });
}
