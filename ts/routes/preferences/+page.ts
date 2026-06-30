// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { autoSavingPrefs } from "$lib/sveltelib/preferences";
import type { PageLoad } from "./$types";
import { getConfigJsonObject, setConfigJsonObject } from "./json";

const CONFIG_KEY = "experimentalFeatures";

export const load = (async () => {
    const labPerfs = await autoSavingPrefs(
        () => getConfigJsonObject(CONFIG_KEY),
        ($config) => setConfigJsonObject(CONFIG_KEY, $config),
    );

    return { labPerfs };
}) satisfies PageLoad;
