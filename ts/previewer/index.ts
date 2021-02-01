import { setupI18n } from "anki/i18n";
import { checkNightMode } from "anki/nightmode";

import { default as Previewer } from "./Previewer.svelte";

export function previewer(target: HTMLDivElement): void {
    const nightMode = checkNightMode();
    setupI18n().then((i18n) => {
        new Previewer({
            target,
            props: {
                i18n,
                nightMode,
            },
        });
    });
}
