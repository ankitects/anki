import { writable } from "svelte/store";
import { isNightMode } from "../../html-filter/helpers";
import { preloadAnswerImages } from "../../reviewer/images";

export function setupReviewer(iframe: HTMLIFrameElement) {
    const cardClass = writable("");

    function updateHtml(htmlString) {
        if (iframe?.contentDocument) {
            const nightMode = isNightMode();
            iframe.contentDocument.body.innerHTML = htmlString;
            iframe.contentDocument.head.innerHTML = document.head.innerHTML;
            iframe.contentDocument.body.className = nightMode
                ? "nightMode card"
                : "card";
            const root = iframe.contentDocument.querySelector("html")!;
            root.className = nightMode
                ? "night-mode"
                : "";
            root.setAttribute("data-bs-theme", nightMode ? "dark" : "light");
            // @ts-ignore
            iframe.contentDocument.pycmd = bridgeCommand;
        }
    }

    function showQuestion(q, a, cc) {
        updateHtml(q);
        // html.set(q);
        cardClass.set(cc);
        preloadAnswerImages(a);
    }

    globalThis._showAnswer = updateHtml;
    globalThis._showQuestion = showQuestion;

    return { cardClass };
}
