// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { autoSavingPrefs } from "$lib/sveltelib/preferences";
import { getColConfig, setColConfig } from "@tslib/profile";
import type { PageLoad } from "./$types";

const CONFIG_KEY = "experimentalFeatures";

async function getPreferences() {
    const resp = await getColConfig(CONFIG_KEY);
    return resp ?? {};
}

export const load = (async () => {
    const labPerfs = await autoSavingPrefs(
        getPreferences,
        ($config) => setColConfig(CONFIG_KEY, $config),
    );
    return { labPerfs };
}) satisfies PageLoad;
