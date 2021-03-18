// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { SvelteComponent } from "svelte";
import { setupI18n } from "anki/i18n";
import { checkNightMode } from "anki/nightmode";
import TagEditorSvelte from "./TagEditor.svelte";


class TagEditor extends HTMLElement {
    component?: SvelteComponent;
    initialTags: string[] = []

    async connectedCallback(): Promise<void> {
        const nightMode = checkNightMode();
        const i18n = await setupI18n()

        this.component = new TagEditorSvelte({
            target: this,
            props: {
                i18n,
                nightMode,
                tags: this.initialTags,
            },
        });
    }

    set tags(newTags: string[]) {
        if (this.component) {
            this.component.$set({ tags: newTags });
        }
        else {
            this.initialTags = newTags;
        }
    }
}

customElements.define("anki-tageditor", TagEditor)
