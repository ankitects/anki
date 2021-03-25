import type { SvelteComponent } from "svelte";
import { setupI18n } from "anki/i18n";
import { checkNightMode } from "anki/nightmode";
import EditorToolbarSvelte from "./EditorToolbar.svelte";

class EditorToolbar extends HTMLElement {
    component?: SvelteComponent;

    async connectedCallback(): Promise<void> {
        const nightMode = checkNightMode();
        const i18n = await setupI18n();

        this.component = new EditorToolbarSvelte({
            target: this,
            props: {
                i18n,
                nightMode,
            },
        });
    }
}

customElements.define("anki-editor-toolbar", EditorToolbar);
