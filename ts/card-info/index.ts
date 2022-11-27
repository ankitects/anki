// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./card-info-base.scss";

import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";

import CardInfo from "./CardInfo.svelte";

const i18n = setupI18n({
    modules: [ModuleName.CARD_STATS, ModuleName.SCHEDULING, ModuleName.STATISTICS],
});

export async function setupCardInfo(
    target: HTMLElement,
    props = {},
): Promise<CardInfo> {
    checkNightMode();
    await i18n;

    return new CardInfo({ target, props });
}

if (window.location.hash.startsWith("#test")) {
    // use #testXXXX where XXXX is card ID to test
    const cardId = parseInt(window.location.hash.substring(0, "#test".length), 10);
    setupCardInfo(document.body).then(
        (cardInfo: CardInfo): Promise<void> => cardInfo.updateStats(cardId),
    );
}
