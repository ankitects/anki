// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { CardAnswer, type NextCardDataResponse_NextCardData } from "@generated/anki/scheduler_pb";
import { compareAnswer, nextCardData, playAvtags } from "@generated/backend";
import { derived, get, writable } from "svelte/store";
import type { InnerReviewerRequest } from "../reviewer-inner/innerReviewerRequest";
import type { ReviewerRequest } from "./reviewerRequest";

export function isNightMode() {
    // https://stackoverflow.com/a/57795518
    // This will be true in browsers if darkmode but also false in the reviewer if darkmode
    // If in the reviewer then this will need to be set by the python instead
    return (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches)
        || document.documentElement.classList.contains("night-mode");
}

export function enableNightMode() {
    document.documentElement.classList.add("night-mode");
    document.documentElement.setAttribute("data-bs-theme", "dark");
}

export function updateNightMode() {
    if (isNightMode()) {
        enableNightMode();
    }
}

const typedAnswerRegex = /\[\[type:(.+?:)?(.+?)\]\]/m;

export class ReviewerState {
    answerHtml = "";
    currentTypedAnswer = "";
    _cardData: NextCardDataResponse_NextCardData | undefined = undefined;
    beginAnsweringMs = Date.now();
    readonly cardClass = writable("");
    readonly answerShown = writable(false);
    readonly cardData = writable<NextCardDataResponse_NextCardData | undefined>(undefined);
    readonly answerButtons = derived(this.cardData, ($cardData) => $cardData?.answerButtons ?? []);

    iframe: HTMLIFrameElement | undefined = undefined;

    onReady() {
        this.iframe!.style.visibility = "visible";
        this.showQuestion(null);
        addEventListener("message", this.onMessage.bind(this));
    }

    async onMessage(e: MessageEvent<ReviewerRequest>) {
        switch (e.data.type) {
            case "audio": {
                const tags = get(this.answerShown) ? this._cardData!.answerAvTags : this._cardData!.questionAvTags;
                playAvtags({ tags: [tags[e.data.index]] });
                break;
            }
            case "typed": {
                this.currentTypedAnswer = e.data.value;
                break;
            }
            case "keypress": {
                this.handleKeyPress(e.data.key);
                break;
            }
        }
    }

    public registerIFrame(iframe: HTMLIFrameElement) {
        this.iframe = iframe;
        iframe.addEventListener("load", this.onReady.bind(this));
    }

    handleKeyPress(key: string) {
        switch (key) {
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

    onKeyDown(e: KeyboardEvent) {
        this.handleKeyPress(e.key);
    }

    public registerShortcuts() {
        document.addEventListener("keydown", this.onKeyDown.bind(this));
    }

    sendInnerRequest(message: InnerReviewerRequest) {
        this.iframe?.contentWindow?.postMessage(message, "*");
    }

    updateHtml(htmlString: string, css?: string, bodyclass?: string) {
        this.sendInnerRequest({ type: "html", value: htmlString, css, bodyclass });
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
        this.updateHtml(question, resp?.nextCard?.css, resp?.nextCard?.bodyClass);
        if (this._cardData?.autoplay) {
            playAvtags({ tags: this._cardData!.questionAvTags });
        }

        this.beginAnsweringMs = Date.now();
    }

    get currentCard() {
        return this._cardData?.queue?.cards[0];
    }

    async showTypedAnswer(html: string) {
        if (!this._cardData?.typedAnswer || !this._cardData.typedAnswerArgs) {
            return html;
        }
        const compareAnswerResp = await compareAnswer({
            expected: this._cardData?.typedAnswer,
            provided: this.currentTypedAnswer,
            combining: !this._cardData.typedAnswerArgs.includes("nc"),
        });
        const display = compareAnswerResp.val;

        console.log({ typedAnswerRegex, html, display });
        return html.replace(typedAnswerRegex, display);
    }

    public async showAnswer() {
        this.answerShown.set(true);
        if (this._cardData?.autoplay) {
            playAvtags({ tags: this._cardData!.answerAvTags });
        }
        this.updateHtml(await this.showTypedAnswer(this._cardData?.back || ""));
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
