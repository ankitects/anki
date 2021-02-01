import type { SvelteComponent } from "svelte/internal";

import { setupI18n } from "anki/i18n";
import { checkNightMode } from "anki/nightmode";

import { default as CardDisplay } from "./CardDisplay.svelte";
// import { default as Navbar } from "./Navbar.svelte";

export function previewer(
    target: HTMLDivElement,
): void {
    const nightMode = checkNightMode();
    setupI18n().then((i18n) => {
        new CardDisplay({
            target,
            props: {
                i18n,
                nightMode,
            },
        });
    });
}
