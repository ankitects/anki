// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getCongratsInfo } from "./lib";
import { setupI18n, ModuleName } from "../lib/i18n";
import { checkNightMode } from "../lib/nightmode";

import CongratsPage from "./CongratsPage.svelte";
import "./congrats-base.css";

const i18n = setupI18n({ modules: [ModuleName.SCHEDULING] });

export async function setupCongrats(): Promise<CongratsPage> {
    checkNightMode();
    await i18n;

    const info = await getCongratsInfo();
    const page = new CongratsPage({
        target: document.body,
        props: { info },
    });

    setInterval(() => {
        getCongratsInfo().then((info) => {
            page.$set({ info });
        });
    }, 60000);

    return page;
}

setupCongrats();
