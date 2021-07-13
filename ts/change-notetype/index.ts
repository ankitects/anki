// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-explicit-any: "off",
 */

import { ChangeNotetypeState, getChangeNotetypeInfo, getNotetypeNames } from "./lib";
import { setupI18n, ModuleName } from "lib/i18n";
import { checkNightMode } from "lib/nightmode";
import ChangeNotetypePage from "./ChangeNotetypePage.svelte";
import { nightModeKey } from "components/context-keys";

export async function changeNotetypePage(
    target: HTMLDivElement,
    oldNotetypeId: number,
    newNotetypeId: number
): Promise<ChangeNotetypePage> {
    const [info, names] = await Promise.all([
        getChangeNotetypeInfo(oldNotetypeId, newNotetypeId),
        getNotetypeNames(),
        setupI18n({
            modules: [ModuleName.ACTIONS, ModuleName.CHANGE_NOTETYPE],
        }),
    ]);

    const nightMode = checkNightMode();
    const context = new Map();
    context.set(nightModeKey, nightMode);

    const state = new ChangeNotetypeState(names, info);
    return new ChangeNotetypePage({
        target,
        props: { state },
        context,
    } as any);
}
