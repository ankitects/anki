// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import {
    CardAnswer,
    type NextCardDataResponse_NextCardData,
} from "@generated/anki/scheduler_pb";
import { nextCardData } from "@generated/backend";
import { writable } from "svelte/store";

export class ReviewerState {
    answerHtml: string = ""
    cardData: NextCardDataResponse_NextCardData | undefined = undefined;
    beginAnsweringMs = Date.now();
    readonly cardClass = writable("");
    
    iframe: HTMLIFrameElement | undefined = undefined;

    onReady() {
        this.iframe?.contentWindow?.postMessage({ type: "nightMode", value: true }, "*");
        this.showQuestion(null);
    }

    public registerIFrame(iframe: HTMLIFrameElement) {
        this.iframe = iframe;
        iframe.addEventListener("load", this.onReady.bind(this));
    }

    updateHtml(htmlString: string) {
        this.iframe?.contentWindow?.postMessage({ type: "html", value: htmlString }, "*");
    }
    
    async showQuestion(answer: CardAnswer | null) {
        const resp = await nextCardData({
            answer: answer || undefined,
        });
        // TODO: "Congratulation screen" logic
        const question = resp.nextCard?.front || "";
        this.updateHtml(question);
    }

    public showAnswer() {
        this.updateHtml(this.cardData?.back || "");
    }

    public easeButtonPressed(rating: number) {
        const states = this.cardData!.states!;

        let newState = ({
            [1]: states.again!,
            [2]: states.hard!,
            [3]: states.good!,
            [4]: states.easy!,
        })[rating]!;

        this.showQuestion(
            new CardAnswer({
                rating: rating,
                currentState: states!.current!,
                newState,
                cardId: this.cardData!.cardId,
                answeredAtMillis: BigInt(Date.now()),
                millisecondsTaken: Date.now() - this.beginAnsweringMs,
            }),
        );
    }
}
