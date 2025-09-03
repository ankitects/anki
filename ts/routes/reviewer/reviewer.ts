import { writable } from "svelte/store";
import { preloadAnswerImages } from "../../reviewer/images";

export function setupReviewer() {
    const html = writable("");
    const cardClass = writable("");

    function showQuestion(q, a, cc) {
        html.set(q);
        cardClass.set(cc);
        preloadAnswerImages(a);
    }

    globalThis._showAnswer = html.set;
    globalThis._showQuestion = showQuestion;

    return { html, cardClass };
}
