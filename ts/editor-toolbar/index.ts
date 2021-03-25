import type { SvelteComponent } from "svelte";
import { checkNightMode } from "anki/nightmode";
import EditorToolbarSvelte from "./EditorToolbar.svelte";

class EditorToolbar extends HTMLElement {
    component?: SvelteComponent;

    connectedCallback(): void {
        const nightMode = checkNightMode();

        this.component = new EditorToolbarSvelte({
            target: this,
            props: {
                nightMode,
            },
        });
    }
}

customElements.define("anki-editor-toolbar", EditorToolbar);
