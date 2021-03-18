// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { SvelteComponent } from "svelte";
import { setupI18n } from "anki/i18n";
import { checkNightMode } from "anki/nightmode";
import TagEditor from "./TagEditor.svelte";

export async function tagEditor(target: HTMLDivElement): Promise<SvelteComponent> {
    const nightMode = checkNightMode();
    const i18n = await setupI18n()

    return new TagEditor({
        target,
        props: {
            i18n,
            nightMode,
        },
    });
}
