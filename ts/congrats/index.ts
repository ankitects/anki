// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./congrats-base.css";

import { ModuleName, setupI18n } from "../lib/i18n";
import { checkNightMode } from "../lib/nightmode";
import { empty, scheduler } from "../lib/proto";
import CongratsPage from "./CongratsPage.svelte";

const i18n = setupI18n({ modules: [ModuleName.SCHEDULING] });

export async function setupCongrats(): Promise<CongratsPage> {
    checkNightMode();
    await i18n;

    const customMountPoint = document.getElementById("congrats");
    const info = await scheduler.congratsInfo(empty);
    const page = new CongratsPage({
        // use #congrats if it exists, otherwise entire body
        target: customMountPoint ?? document.body,
        props: { info },
    });

    // refresh automatically if a custom area not provided
    if (!customMountPoint) {
        setInterval(async () => {
            const info = await scheduler.congratsInfo(empty);
            page.$set({ info });
        }, 60000);
    }

    return page;
}

setupCongrats();
