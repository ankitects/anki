import { setupI18n } from "anki/i18n";
import { checkNightMode } from "anki/nightmode";

import { PreviewerInput, PreviewerMode } from "./utils";
import { default as Previewer } from "./Previewer.svelte";

const target = document.getElementById("main") as Element;
const nightMode = checkNightMode();
const previewer = setupI18n().then(
    (i18n) =>
        new Previewer({
            target,
            props: {
                i18n,
                nightMode,
            },
        })
);

export function setPreviewerNote(input: PreviewerInput, cardTypeNames: string[]): void {
    previewer.then((previewer: Previewer) => {
        previewer.$set({
            input,
            cardTypeNames,
            mode: PreviewerMode.StandardCards,
        });
        return previewer;
    });
}
