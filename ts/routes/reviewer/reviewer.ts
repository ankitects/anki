import { bridgeCommand } from "@tslib/bridgecommand";
import { writable } from "svelte/store";
import { preloadAnswerImages } from "../../reviewer/images";

export function setupReviewer(iframe: HTMLIFrameElement) {
    const cardClass = writable("");

    function updateHtml(htmlString) {
        iframe.contentWindow?.postMessage({ type: "html", value: htmlString });
    }

    function showQuestion(q, a, cc) {
        updateHtml(q);
        // html.set(q);
        cardClass.set(cc);
        preloadAnswerImages(a);
    }

    addEventListener("message", (e) => {
        switch (e.data.type) {
            case "ready":
                // TODO This should probably be a "ready" command now that it is part of the actual reviewer,
                // Currently this depends on the reviewer component mounting after the bottom-reviewer which it should but seems hacky.
                // Maybe use a counter with a counter.subscribe($counter == 2 then call("ready"))
                bridgeCommand("bottomReady");
                iframe.contentWindow?.postMessage({ type: "nightMode", value: true });
                break;
            default:
                console.warn(`Unknown message type: ${e.data.type}`);
                break;
        }
    });

    globalThis._showAnswer = updateHtml;
    globalThis._showQuestion = showQuestion;

    return { cardClass };
}
