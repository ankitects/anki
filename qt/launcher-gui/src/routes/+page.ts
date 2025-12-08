// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getLangs, getMirrors, getState, getUninstallInfo } from "@generated/backend-launcher";
import { ModuleName, setupI18n } from "@tslib/i18n";
import type { PageLoad } from "./$types";

export const load = (async () => {
    const i18nPromise = setupI18n({ modules: [ModuleName.LAUNCHER] }, true);
    const langsPromise = getLangs({});
    const statePromise = getState({});
    const mirrorsPromise = getMirrors({});
    const uninstallInfoPromise = getUninstallInfo({});

    const [_, { userLocale, langs }, { kind: state }, { mirrors }, uninstallInfo] = await Promise.all([
        i18nPromise,
        langsPromise,
        statePromise,
        mirrorsPromise,
        uninstallInfoPromise,
    ]);

    return { langs, userLocale, state, mirrors, uninstallInfo };
}) satisfies PageLoad;
