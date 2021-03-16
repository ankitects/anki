// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { setupI18n } from "anki/i18n";
import { checkNightMode } from "anki/nightmode";
import TagEditor from "./GraphsPage.svelte";

export function tagEditor(target: HTMLDivElement): void {
    const nightMode = checkNightMode();

    setupI18n().then((i18n) => {
        new TagEditor({
            target,
            props: {
                i18n,
                nightMode,
            },
        });
    });
}
