import { writable } from "svelte/store"
import { preloadAnswerImages } from "../../reviewer/images"

export function setupReviewer() {
    const html = writable("")

    function showQuestion(q, a, bodyclass) {
        html.set(q)
        preloadAnswerImages(a)
    }

    globalThis._showAnswer = html.set
    globalThis._showQuestion = showQuestion

    return {html}
}