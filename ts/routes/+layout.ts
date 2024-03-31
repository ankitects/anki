// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { setupGlobalI18n } from "@tslib/i18n";
import { checkNightMode } from "@tslib/nightmode";

import type { LayoutLoad } from "./$types";

export const ssr = false;
export const prerender = false;

export const load: LayoutLoad = async () => {
    checkNightMode();
    await setupGlobalI18n();
};
