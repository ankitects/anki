// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { setupI18n } from "../i18n";
import CongratsPage from "./CongratsPage.svelte";
import { getCongratsInfo } from "./congrats";
import { checkNightMode } from "../nightmode";

export async function congrats(target: HTMLDivElement): Promise<void> {
    checkNightMode();
    const i18n = await setupI18n();
    const info = await getCongratsInfo();
    new CongratsPage({
        target,
        props: { info, i18n },
    });
}
