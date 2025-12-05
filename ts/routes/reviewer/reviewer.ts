// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import type { AVTag } from "@generated/anki/card_rendering_pb";
import type { UndoStatus } from "@generated/anki/collection_pb";
import { DeckConfig_Config_AnswerAction, DeckConfig_Config_QuestionAction } from "@generated/anki/deck_config_pb";
import { ReviewerActionRequest_ReviewerAction } from "@generated/anki/frontend_pb";
import {
    BuryOrSuspendCardsRequest_Mode,
    CardAnswer,
    type NextCardDataResponse_NextCardData,
} from "@generated/anki/scheduler_pb";
import {
    addNoteTags,
    buryOrSuspendCards,
    compareAnswer,
    getConfigJson,
    nextCardData,
    playAvtags,
    redo,
    removeNotes,
    removeNoteTags,
    reviewerAction,
    setConfigJson,
    setFlag,
    undo,
} from "@generated/backend";
import * as tr from "@generated/ftl";
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
const TOOLTIP_TIMEOUT_MS = 2000;

export class ReviewerState {
    answerHtml = "";
    currentTypedAnswer = "";
    _cardData: NextCardDataResponse_NextCardData | undefined = undefined;
    beginAnsweringMs = Date.now();
    answerMs: number | undefined = undefined;
    readonly cardClass = writable("");
    readonly answerShown = writable(false);
    readonly cardData = writable<NextCardDataResponse_NextCardData | undefined>(undefined);
    readonly answerButtons = derived(this.cardData, ($cardData) => $cardData?.answerButtons ?? []);
    tooltipMessageTimeout: ReturnType<typeof setTimeout> | undefined;
    readonly tooltipMessage = writable("");
    readonly tooltipShown = writable(false);
    readonly flag = writable(0);
    readonly marked = writable(false);
    readonly autoAdvance = writable(false);
    undoStatus: UndoStatus | undefined = undefined;
    autoAdvanceQuestionTimeout: ReturnType<typeof setTimeout> | undefined;
    autoAdvanceAnswerTimeout: ReturnType<typeof setTimeout> | undefined;
    _answerShown = false;

    iframe: HTMLIFrameElement | undefined = undefined;

    constructor() {
        this.autoAdvance.subscribe($autoAdvance => {
            if (this._answerShown) {
                this.updateAutoAdvanceAnswer();
            } else {
                this.updateAutoAdvanceQuestion();
            }
            if (!$autoAdvance) {
                clearInterval(this.autoAdvanceQuestionTimeout);
                clearInterval(this.autoAdvanceAnswerTimeout);
            }
        });
    }

    public toggleAutoAdvance() {
        this.autoAdvance.update(($autoAdvance) => {
            // Reversed because the $autoAdvance will be flipped by the return.
            this.showTooltip($autoAdvance ? tr.actionsAutoAdvanceDeactivated() : tr.actionsAutoAdvanceActivated());
            return !$autoAdvance;
        });
    }

    async onReady() {
        const { json } = await getConfigJson({ val: "reviewerStorage" });
        this.sendInnerRequest({ type: "setstorage", json_buffer: json });
        this.showQuestion(null);
        addEventListener("message", this.onMessage.bind(this));
    }

    async onMessage(e: MessageEvent<ReviewerRequest>) {
        switch (e.data.type) {
            case "audio": {
                const tags = get(this.answerShown) ? this._cardData!.answerAvTags : this._cardData!.questionAvTags;
                this.playAudio([tags[e.data.index]]);
                break;
            }
            case "typed": {
                this.currentTypedAnswer = e.data.value;
                break;
            }
            case "keypress": {
                // This is a hacky fix because otherwise while focused on the reviewer-bottom, pressing m only keeps the menu open for the duration of the button press (using "keyup" in the shortcut in More.svelte fixed this)
                const forceKeyUp = e.data.eventInit.key?.toLowerCase() == "m";
                document.dispatchEvent(new KeyboardEvent(forceKeyUp ? "keyup" : "keydown", e.data.eventInit));
                break;
            }
            case "closemenu": {
                document.dispatchEvent(new CustomEvent("closemenu"));
                break;
            }
            case "setstorage": {
                setConfigJson({
                    key: "reviewerStorage",
                    valueJson: e.data.json_buffer,
                    undoable: false,
                });
            }
        }
    }

    public registerIFrame(iframe: HTMLIFrameElement) {
        this.iframe = iframe;
        iframe.addEventListener("load", this.onReady.bind(this));
    }

    public refresh() {
        this.showQuestion(null);
    }

    reviewerAction(menu: ReviewerActionRequest_ReviewerAction) {
        reviewerAction({ menu, currentCardId: this.currentCard?.card?.id });
    }

    public displayEditMenu() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.EditCurrent);
    }

    public displaySetDueDateMenu() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.SetDueDate);
    }

    public displayCardInfoMenu() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.CardInfo);
    }

    public displayPreviousCardInfoMenu() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.PreviousCardInfo);
    }

    public displayCreateCopyMenu() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.CreateCopy);
    }

    public displayForgetMenu() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.Forget);
    }

    public displayOptionsMenu() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.Options);
    }

    public displayOverview() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.Overview);
    }

    public playAudio(tags: AVTag[]) {
        if (tags.length) {
            playAvtags({ tags });
        }
    }

    maybeAutoPlayAudio(tags: AVTag[]) {
        if (this._cardData?.autoplay) {
            this.playAudio(tags);
        }
    }

    public replayAudio() {
        if (this._answerShown) {
            this.playAudio(this._cardData!.answerAvTags);
        } else {
            this.playAudio(this._cardData!.questionAvTags);
        }
    }

    public pauseAudio() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.PauseAudio);
    }

    public AudioSeekBackward() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.SeekBackward);
    }

    public AudioSeekForward() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.SeekForward);
    }

    public RecordVoice() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.RecordVoice);
    }

    public ReplayRecorded() {
        this.reviewerAction(ReviewerActionRequest_ReviewerAction.ReplayRecorded);
    }

    public async toggleMarked() {
        if (this._cardData && this.currentCard?.card?.noteId) {
            const noteIds = [this.currentCard.card.noteId];
            if (this._cardData.marked) {
                await removeNoteTags({ noteIds, tags: "marked" });
                this.setUndo(tr.actionsRemoveTag());
            } else {
                await addNoteTags({ noteIds, tags: "marked" });
                this.setUndo(tr.actionsUpdateTag());
            }
            this.marked.update($marked => !$marked);
            this._cardData.marked = !this._cardData.marked;
        }
    }

    public changeFlag(index: number) {
        this.flag.update($flag => {
            if ($flag === index) {
                index = 0;
            }
            setFlag({ cardIds: [this.currentCard!.card!.id], flag: index });
            return index;
        });
    }

    public showTooltip(message: string) {
        clearTimeout(this.tooltipMessageTimeout);
        this.tooltipMessage.set(message);
        this.tooltipShown.set(true);
        this.tooltipMessageTimeout = setTimeout(() => {
            this.tooltipShown.set(false);
        }, TOOLTIP_TIMEOUT_MS);
    }

    public setUndo(status: string) {
        // For a list of statuses, see
        // https://github.com/ankitects/anki/blob/acdf486b290bd47d13e2e880fbb1c14773899091/rslib/src/ops.rs#L57
        if (this.undoStatus) { // Skip if "undoStatus" is disabled / not set
            this.undoStatus.undo = status;
        }
    }

    public setBuryOrSuspendUndo(suspend: boolean) {
        this.setUndo(suspend ? tr.studyingSuspend() : tr.studyingBury());
    }

    public async buryOrSuspendCurrentCard(suspend: boolean) {
        const mode = suspend ? BuryOrSuspendCardsRequest_Mode.SUSPEND : BuryOrSuspendCardsRequest_Mode.BURY_USER;
        if (this.currentCard?.card?.id) {
            await buryOrSuspendCards({
                cardIds: [this.currentCard.card.id],
                noteIds: [],
                mode,
            });
            this.showTooltip(suspend ? tr.studyingCardSuspended() : tr.studyingCardsBuried({ count: 1 }));
            this.setBuryOrSuspendUndo(suspend);
            this.refresh();
        }
    }

    public async buryOrSuspendCurrentNote(suspend: boolean) {
        const mode = suspend ? BuryOrSuspendCardsRequest_Mode.SUSPEND : BuryOrSuspendCardsRequest_Mode.BURY_USER;
        if (this.currentCard?.card?.noteId) {
            const op = await buryOrSuspendCards({
                cardIds: [],
                noteIds: [this.currentCard.card.noteId],
                mode,
            });
            this.showTooltip(suspend ? tr.studyingNoteSuspended() : tr.studyingCardsBuried({ count: op.count }));
            this.setBuryOrSuspendUndo(suspend);
            this.refresh();
        }
    }

    public async deleteCurrentNote() {
        if (this.currentCard?.card?.noteId) {
            const op = await removeNotes({ noteIds: [this.currentCard.card.noteId], cardIds: [] });
            this.showTooltip(tr.browsingCardsDeleted({ count: op.count }));
            this.setUndo(tr.studyingDeleteNote());
            this.refresh();
        }
    }

    async handleKeyPress(key: string, ctrl: boolean, shift: boolean) {
        key = key.toLowerCase();
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
            case " ":
            case "enter": {
                if (!this._answerShown) {
                    this.showAnswer();
                } else if (this._cardData?.acceptEnter ?? true) {
                    this.easeButtonPressed(2);
                }
                break;
            }
            case "z": {
                if (ctrl) {
                    if (shift && this.undoStatus?.redo) {
                        const op = await redo({});
                        this.showTooltip(tr.undoActionRedone({ action: op.operation }));
                        this.undoStatus = op.newStatus;
                    } else if (this.undoStatus?.undo) {
                        const op = await undo({});
                        this.showTooltip(tr.undoActionUndone({ action: op.operation }));
                        this.undoStatus = op.newStatus;
                    } else {
                        this.showTooltip(shift ? tr.actionsNothingToRedo() : tr.actionsNothingToUndo());
                    }
                    this.refresh();
                }
                break;
            }
            case "e": {
                if (!ctrl) {
                    this.displayEditMenu();
                    break;
                }
            }
        }
    }

    onKeyDown(e: KeyboardEvent) {
        if (e.repeat) {
            return;
        }
        if (e.key == "Enter") {
            e.preventDefault();
        }
        this.handleKeyPress(e.key, e.ctrlKey, e.shiftKey);
    }

    public registerShortcuts() {
        window.addEventListener("keydown", this.onKeyDown.bind(this));
    }

    sendInnerRequest(message: InnerReviewerRequest) {
        this.iframe?.contentWindow?.postMessage(message, "*");
    }

    updateHtml(htmlString: string, css?: string, bodyclass?: string, preload?: string) {
        this.sendInnerRequest({ type: "html", value: htmlString, css, bodyclass, preload });
    }

    updateAutoAdvanceQuestion() {
        clearTimeout(this.autoAdvanceAnswerTimeout);
        if (get(this.autoAdvance) && this._cardData!.autoAdvanceQuestionSeconds) {
            const action = ({
                [DeckConfig_Config_QuestionAction.SHOW_ANSWER]: () => {
                    this.showAnswer();
                },
                [DeckConfig_Config_QuestionAction.SHOW_REMINDER]: () => {
                    this.showTooltip(tr.studyingQuestionTimeElapsed());
                },
            })[this._cardData!.autoAdvanceQuestionAction];

            this.autoAdvanceQuestionTimeout = setTimeout(action, this._cardData!.autoAdvanceQuestionSeconds * 1000);
        }
    }

    updateAutoAdvanceAnswer() {
        clearTimeout(this.autoAdvanceQuestionTimeout);
        if (get(this.autoAdvance) && this._cardData?.autoAdvanceAnswerSeconds) {
            const action = ({
                [DeckConfig_Config_AnswerAction.ANSWER_AGAIN]: () => {
                    this.easeButtonPressed(0);
                },
                [DeckConfig_Config_AnswerAction.ANSWER_HARD]: () => {
                    this.easeButtonPressed(1);
                },
                [DeckConfig_Config_AnswerAction.ANSWER_GOOD]: () => {
                    this.easeButtonPressed(2);
                },
                [DeckConfig_Config_AnswerAction.BURY_CARD]: () => {
                    this.buryOrSuspendCurrentCard(false);
                },
                [DeckConfig_Config_AnswerAction.SHOW_REMINDER]: () => {
                    this.showTooltip(tr.studyingAnswerTimeElapsed());
                },
            })[this._cardData.autoAdvanceAnswerAction];

            this.autoAdvanceAnswerTimeout = setTimeout(action, this._cardData.autoAdvanceAnswerSeconds * 1000);
        }
    }

    async showQuestion(answer: CardAnswer | null) {
        if (answer !== null) {
            this.setUndo(tr.actionsAnswerCard());
        }

        this._answerShown = false;
        const resp = await nextCardData({
            answer: answer || undefined,
        });

        if (!resp.nextCard) {
            this.displayOverview();
            return;
        }

        this._cardData = resp.nextCard;
        this.cardData.set(this._cardData);
        this.flag.set(this.currentCard?.card?.flags ?? 0);
        this.marked.set(this._cardData.marked);
        this.answerShown.set(false);

        const question = resp.nextCard?.front || "";
        this.updateHtml(question, resp?.nextCard?.css, resp?.nextCard?.bodyClass, resp?.preload);
        this.iframe!.style.visibility = "visible";
        this.maybeAutoPlayAudio(this._cardData.questionAvTags);
        this.beginAnsweringMs = Date.now();
        this.answerMs = undefined;
        this.updateAutoAdvanceQuestion();
    }

    get currentCard() {
        return this._cardData?.queue?.cards[0];
    }

    async showTypedAnswer(html: string) {
        if (this._cardData?.typedAnswer === undefined) {
            return html;
        }
        const args = this._cardData.typedAnswer.args;
        const compareAnswerResp = await compareAnswer({
            expected: this._cardData.typedAnswer.text,
            provided: this.currentTypedAnswer,
            combining: !args.includes("nc"),
        });

        const prefix = args.includes("cloze") ? "<br/>" : "";
        const display = prefix + compareAnswerResp.val;

        return html.replace(typedAnswerRegex, display);
    }

    public async showAnswer() {
        this.answerShown.set(true);
        this._answerShown = true;
        this.maybeAutoPlayAudio(this._cardData!.answerAvTags);
        this.answerMs = Date.now();
        this.updateHtml(await this.showTypedAnswer(this._cardData?.back || ""));
        this.updateAutoAdvanceAnswer();
    }

    public easeButtonPressed(rating: number) {
        if (!this._answerShown) {
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
