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

export function setPreviewerNote(input: PreviewerInput): void {
    previewer.then((previewer: Previewer) => {
        const cards = input.map(([front, back]) => [front, back]);
        const cardTypeNames = input.map(([,, cardTypeName]) => cardTypeName);

        previewer.$set({
            cards,
            cardTypeNames,
            mode: PreviewerMode.StandardCards,
        });

        return previewer;
    });
}

export function setPreviewerClozeNote(input: PreviewerInput): void {
    previewer.then((previewer: Previewer) => {
        previewer.$set({
            cards: input,
            mode: PreviewerMode.ClozeCards,
        });

        return previewer;
    });
}
