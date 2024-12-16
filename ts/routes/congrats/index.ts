// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import "./congrats-base.scss";

import { congratsInfo } from "@generated/backend";
import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";

import CongratsPage from "./CongratsPage.svelte";

const i18n = setupI18n({ modules: [ModuleName.SCHEDULING] });

export async function setupCongrats(): Promise<CongratsPage> {
    checkNightMode();
    await i18n;

    const customMountPoint = document.getElementById("congrats");
    const info = await congratsInfo({});
    const page = new CongratsPage({
        // use #congrats if it exists, otherwise entire body
        target: customMountPoint ?? document.body,
        props: { info, refreshPeriodically: !customMountPoint },
    });

    return page;
}

setupCongrats();
