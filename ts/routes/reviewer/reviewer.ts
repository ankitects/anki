// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { CardAnswer, type NextCardDataResponse_NextCardData } from "@generated/anki/scheduler_pb";
import { nextCardData } from "@generated/backend";
import { derived, get, writable } from "svelte/store";

export class ReviewerState {
    answerHtml = "";
    _cardData: NextCardDataResponse_NextCardData | undefined = undefined;
    beginAnsweringMs = Date.now();
    readonly cardClass = writable("");
    readonly answerShown = writable(false);
    readonly cardData = writable<NextCardDataResponse_NextCardData | undefined>(undefined);
    readonly answerButtons = derived(this.cardData, ($cardData) => $cardData?.answerButtons ?? []);

    iframe: HTMLIFrameElement | undefined = undefined;

    onReady() {
        this.iframe?.contentWindow?.postMessage({ type: "nightMode", value: true }, "*");
        this.showQuestion(null);
    }

    public registerIFrame(iframe: HTMLIFrameElement) {
        this.iframe = iframe;
        iframe.addEventListener("load", this.onReady.bind(this));
    }

    onKeyDown(e: KeyboardEvent) {
        switch (e.key) {
            case "1": {
                this.easeButtonPressed(0);
                break;
            }
            case "2": {
                this.easeButtonPressed(1);
                break;
            }
            case "3": {
                this.easeButtonPressed(2);
                break;
            }
            case "4": {
                this.easeButtonPressed(3);
                break;
            }
            case " ": {
                if (!get(this.answerShown)) {
                    this.showAnswer();
                } else {
                    this.easeButtonPressed(2);
                }
                break;
            }
        }
    }

    public registerShortcuts() {
        document.addEventListener("keydown", this.onKeyDown.bind(this));
    }

    updateHtml(htmlString: string) {
        this.iframe?.contentWindow?.postMessage({ type: "html", value: htmlString }, "*");
    }

    async showQuestion(answer: CardAnswer | null) {
        const resp = await nextCardData({
            answer: answer || undefined,
        });

        // TODO: "Congratulation screen" logic
        this._cardData = resp.nextCard;
        this.cardData.set(this._cardData);
        this.answerShown.set(false);

        const question = resp.nextCard?.front || "";
        this.updateHtml(question);

        this.beginAnsweringMs = Date.now();
    }

    get currentCard() {
        return this._cardData?.queue?.cards[0];
    }

    public showAnswer() {
        this.answerShown.set(true);
        this.updateHtml(this._cardData?.back || "");
    }

    public easeButtonPressed(rating: number) {
        if (!get(this.answerShown)) {
            return;
        }

        const states = this.currentCard!.states!;

        const newState = [
            states.again!,
            states.hard!,
            states.good!,
            states.easy!,
        ][rating]!;

        this.showQuestion(
            new CardAnswer({
                rating: rating,
                currentState: states!.current!,
                newState,
                cardId: this.currentCard?.card?.id,
                answeredAtMillis: BigInt(Date.now()),
                millisecondsTaken: Date.now() - this.beginAnsweringMs,
            }),
        );
    }
}
