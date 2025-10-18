// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getLangs, getMirrors, getOptions } from "@generated/backend-launcher";
import { ModuleName, setupI18n } from "@tslib/i18n";
import type { PageLoad } from "./$types";

export const load = (async () => {
    const i18nPromise = setupI18n({ modules: [ModuleName.LAUNCHER] }, true);
    const langsPromise = getLangs({});
    const optionsPromise = getOptions({});
    const mirrorsPromise = getMirrors({});

    const [_, { userLocale, langs }, options, { mirrors }] = await Promise.all([
        i18nPromise,
        langsPromise,
        optionsPromise,
        mirrorsPromise,
    ]);

    return { langs, userLocale, options, mirrors };
}) satisfies PageLoad;
