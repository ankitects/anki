// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { autoSavingPrefs } from "$lib/sveltelib/preferences";
import { getColConfig, setColConfig } from "@tslib/profile";
import type { PageLoad } from "./$types";

const CONFIG_KEY = "experimentalFeatures";

export const load = (async () => {
    const labPerfs = await autoSavingPrefs(
        () => getColConfig(CONFIG_KEY) ?? {},
        ($config) => setColConfig(CONFIG_KEY, $config),
    );

    return { labPerfs };
}) satisfies PageLoad;
