// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { setupI18n, ModuleName } from "../lib/i18n";
import { checkNightMode } from "../lib/nightmode";

import CardInfo from "./CardInfo.svelte";

export async function cardInfo(target: HTMLDivElement): Promise<CardInfo> {
    checkNightMode();
    await setupI18n({
        modules: [ModuleName.CARD_STATS, ModuleName.SCHEDULING, ModuleName.STATISTICS],
    });
    return new CardInfo({ target });
}
