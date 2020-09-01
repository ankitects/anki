// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { setupI18n } from "../i18n";
import GraphsPage from "./GraphsPage.svelte";
import { checkNightMode } from "../nightmode";

export function graphs(target: HTMLDivElement): void {
    setupI18n().then((i18n) => {
        new GraphsPage({
            target,
            props: { i18n, nightMode: checkNightMode() },
        });
    });
}
