// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./card-info-base.css";

import { ModuleName, setupI18n } from "../lib/i18n";
import { checkNightMode } from "../lib/nightmode";
import CardInfo from "./CardInfo.svelte";

const i18n = setupI18n({
    modules: [ModuleName.CARD_STATS, ModuleName.SCHEDULING, ModuleName.STATISTICS],
});

export async function setupCardInfo(): Promise<CardInfo> {
    checkNightMode();
    await i18n;

    return new CardInfo({ target: document.body, props: { includeRevlog: true } });
}

export const cardInfoPromise = setupCardInfo();

if (window.location.hash.startsWith("#test")) {
    // use #testXXXX where XXXX is card ID to test
    const cardId = parseInt(window.location.hash.substr("#test".length), 10);
    cardInfoPromise.then((cardInfo: CardInfo): void => cardInfo.$set({ cardId }));
}
