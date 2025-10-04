// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import {
    CardAnswer,
    type NextCardDataResponse_NextCardData,
} from "@generated/anki/scheduler_pb";
import { nextCardData } from "@generated/backend";
import { bridgeCommand } from "@tslib/bridgecommand";
import { writable } from "svelte/store";

export function setupReviewer(iframe: HTMLIFrameElement) {
    const cardClass = writable("");
    let answer_html = "";
    let cardData: NextCardDataResponse_NextCardData | undefined = undefined;
    let startAnswering = Date.now();

    function updateHtml(htmlString) {
        iframe.contentWindow?.postMessage({ type: "html", value: htmlString }, "*");
    }

    async function showQuestion(answer: CardAnswer | null) {
        const resp = await nextCardData({
            answer: answer || undefined,
        });
        // TODO: "Congratulation screen" logic
        const question = resp.nextCard?.front || "";
        answer_html = resp.nextCard?.back || "";
        cardData = resp.nextCard;
        console.log({ resp });
        updateHtml(question);
    }

    function showAnswer() {
        updateHtml(answer_html);
    }

    function onReady() {
        iframe.contentWindow?.postMessage({ type: "nightMode", value: true }, "*");
        showQuestion(null);
    }

    function easeButtonPressed(rating: number) {
        const states = cardData!.states!;

        let newState = ({
            [1]: states.again!,
            [2]: states.hard!,
            [3]: states.good!,
            [4]: states.easy!,
        })[rating]!;

        showQuestion(
            new CardAnswer({
                rating: rating,
                currentState: states!.current!,
                newState,
                cardId: cardData!.cardId,
                answeredAtMillis: BigInt(Date.now()),
                millisecondsTaken: Date.now() - startAnswering,
            }),
        );
    }

    iframe?.addEventListener("load", onReady);

    addEventListener("message", (e) => {
        switch (e.data.type) {
            case "pycmd": {
                const cmd = e.data.value as string;
                if (cmd.startsWith("play:")) {
                    bridgeCommand(e.data.value);
                } else {
                    console.error("pycmd command is either invalid or forbidden:", cmd);
                }
                break;
            }
            default: {
                console.warn(`Unknown message type: ${e.data.type}`);
                break;
            }
        }
    });

    globalThis._showQuestion = showQuestion;
    globalThis._showAnswer = showAnswer;

    return { cardClass, easeButtonPressed };
}
