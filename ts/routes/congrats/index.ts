// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
//
// This old non-Sveltekit entrypoint has been preserved for AnkiWeb compatibility,
// and can't yet be removed. AnkiWeb loads the generated .js file into an existing
// page, and mounts into a div with 'id=congrats'. Unlike the desktop, it does not
// auto-refresh (to reduce the load on AnkiWeb).

import { congratsInfo } from "@generated/backend";
import { ModuleName, setupI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";
import { mount } from "svelte";

import CongratsPage from "./CongratsPage.svelte";

const i18n = setupI18n({ modules: [ModuleName.SCHEDULING] });

export async function setupCongrats(): Promise<void> {
    checkNightMode();
    await i18n;

    const customMountPoint = document.getElementById("congrats");
    const info = await congratsInfo({});
    const props = { info, refreshPeriodically: false };
    mount(
        CongratsPage, // use #congrats if it exists, otherwise entire body
        { target: customMountPoint ?? document.body, props },
    );
    return;
}

setupCongrats();
