// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getCongratsInfo } from "./lib";
import { setupI18n, ModuleName } from "lib/i18n";
import { checkNightMode } from "lib/nightmode";

import CongratsPage from "./CongratsPage.svelte";

export async function congrats(target: HTMLDivElement): Promise<void> {
    checkNightMode();
    await setupI18n({ modules: [ModuleName.SCHEDULING] });
    const info = await getCongratsInfo();
    new CongratsPage({
        target,
        props: { info },
    });
}
