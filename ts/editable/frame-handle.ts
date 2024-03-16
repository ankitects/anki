// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getSelection, isSelectionCollapsed } from "@tslib/cross-browser";
import { elementIsEmpty, nodeIsElement, nodeIsText } from "@tslib/dom";
import { on } from "@tslib/events";
import type { Unsubscriber } from "svelte/store";
import { get } from "svelte/store";

import { moveChildOutOfElement } from "$lib/domlib/move-nodes";
import { placeCaretAfter } from "$lib/domlib/place-caret";
import { isComposing } from "$lib/sveltelib/composition";

import type { FrameElement } from "./frame-element";

/**
 * The frame handle also needs some awareness that it's hosted below
 * the frame
 */
export const frameElementTagName = "anki-frame";

/**
 * I originally used a zero width space, however, in contentEditable, if
 * a line ends in a zero width space, and you click _after_ the line,
 * the caret will be placed _before_ the zero width space.
 * Instead I use a hairline space.
 */
const spaceCharacter = "\u200a";
const spaceRegex = /[\u200a]/g;

export function isFrameHandle(node: unknown): node is FrameHandle {
    return node instanceof FrameHandle;
}

function skippableNode(handleElement: FrameHandle, node: Node): boolean {
    /**
     * We only want to move nodes, which are direct descendants of the FrameHandle
     * MutationRecords however might include nodes which were directly removed again
     */
    return (
        (nodeIsText(node)
            && (node.data === spaceCharacter || node.data.length === 0))
        || !Array.prototype.includes.call(handleElement.childNodes, node)
    );
}

function restoreHandleContent(mutations: MutationRecord[]): void {
    let referenceNode: Node | null = null;

    for (const mutation of mutations) {
        const target = mutation.target;

        if (mutation.type === "childList") {
            if (!isFrameHandle(target)) {
                /* nested insertion */
                continue;
            }

            const handleElement = target;
            const frameElement = handleElement.parentElement as FrameElement;

            for (const node of mutation.addedNodes) {
                if (skippableNode(handleElement, node)) {
                    continue;
                }

                if (
                    nodeIsElement(node)
                    && !elementIsEmpty(node)
                    && (node.textContent === spaceCharacter
                        || node.textContent?.length === 0)
                ) {
                    /**
                     * When we surround the spaceCharacter of the frame handle
                     */
                    node.replaceWith(new Text(spaceCharacter));
                } else {
                    referenceNode = moveChildOutOfElement(
                        frameElement,
                        node,
                        handleElement.placement,
                    );
                }
            }
        } else if (mutation.type === "characterData") {
            if (
                !nodeIsText(target)
                || !isFrameHandle(target.parentElement)
                || skippableNode(target.parentElement, target)
                || target.parentElement.unsubscribe
            ) {
                continue;
            }
            if (get(isComposing)) {
                target.parentElement.subscribeToCompositionEvent();
                continue;
            }

            referenceNode = target.parentElement.moveTextOutOfFrame(target.data);
        }
    }

    if (referenceNode) {
        placeCaretAfter(referenceNode);
    }
}

const handleObserver = new MutationObserver(restoreHandleContent);
const handles: Set<FrameHandle> = new Set();

type Placement = Extract<InsertPosition, "beforebegin" | "afterend">;

export abstract class FrameHandle extends HTMLElement {
    static get observedAttributes(): string[] {
        return ["data-frames"];
    }

    /**
     * When a deletion is trigger with a FrameHandle selected, it will be treated
     * differently depending on whether it is selected:
     * - If partially selected, it should be restored (unless the frame element
     * is also selected).
     * - Otherwise, it should be deleted along with the frame element.
     */
    partiallySelected = false;
    frames?: string;
    abstract placement: Placement;
    unsubscribe: Unsubscriber | null;

    constructor() {
        super();
        handleObserver.observe(this, {
            childList: true,
            subtree: true,
            characterData: true,
        });
        this.unsubscribe = null;
    }

    attributeChangedCallback(name: string, old: string, newValue: string): void {
        if (newValue === old) {
            return;
        }

        switch (name) {
            case "data-frames":
                this.frames = newValue;
                break;
        }
    }

    abstract getFrameRange(): Range;

    invalidSpace(): boolean {
        return (
            !this.firstChild
            || !(nodeIsText(this.firstChild) && this.firstChild.data === spaceCharacter)
        );
    }

    refreshSpace(): void {
        while (this.firstChild) {
            this.removeChild(this.firstChild);
        }

        this.append(new Text(spaceCharacter));
    }

    hostedUnderFrame(): boolean {
        return this.parentElement!.tagName === frameElementTagName.toUpperCase();
    }

    connectedCallback(): void {
        if (this.invalidSpace()) {
            this.refreshSpace();
        }

        if (!this.hostedUnderFrame()) {
            const range = this.getFrameRange();

            const frameElement = document.createElement(
                frameElementTagName,
            ) as FrameElement;
            frameElement.dataset.frames = this.frames;

            range.surroundContents(frameElement);
        }

        handles.add(this);
    }

    removeMoveIn?: () => void;

    disconnectedCallback(): void {
        handles.delete(this);

        this.removeMoveIn?.();
        this.removeMoveIn = undefined;
        this.unsubscribeToCompositionEvent();
    }

    abstract notifyMoveIn(offset: number): void;

    moveTextOutOfFrame(data: string): Text {
        const frameElement = this.parentElement! as FrameElement;
        const cleaned = data.replace(spaceRegex, "");
        const text = new Text(cleaned);

        if (this.placement === "beforebegin") {
            frameElement.before(text);
        } else if (this.placement === "afterend") {
            frameElement.after(text);
        }
        this.refreshSpace();
        return text;
    }

    /**
     * https://github.com/ankitects/anki/issues/2251
     *
     * Work around the issue by not moving the input string while an IME session
     * is active, and moving the final output from IME only after the session ends.
     */
    subscribeToCompositionEvent(): void {
        this.unsubscribe = isComposing.subscribe((composing) => {
            if (!composing) {
                if (this.firstChild && nodeIsText(this.firstChild)) {
                    placeCaretAfter(this.moveTextOutOfFrame(this.firstChild.data));
                }
                this.unsubscribeToCompositionEvent();
            }
        });
    }

    unsubscribeToCompositionEvent(): void {
        this.unsubscribe?.();
        this.unsubscribe = null;
    }
}

export class FrameStart extends FrameHandle {
    static tagName = "frame-start";
    placement: Placement;

    constructor() {
        super();
        this.placement = "beforebegin";
    }

    getFrameRange(): Range {
        const range = new Range();
        range.setStartBefore(this);

        const maybeFramed = this.nextElementSibling;

        if (maybeFramed?.matches(this.frames ?? ":not(*)")) {
            const maybeHandleEnd = maybeFramed.nextElementSibling;

            range.setEndAfter(
                maybeHandleEnd?.tagName.toLowerCase() === FrameStart.tagName
                    ? maybeHandleEnd
                    : maybeFramed,
            );
        } else {
            range.setEndAfter(this);
        }

        return range;
    }

    notifyMoveIn(offset: number): void {
        if (offset === 1) {
            this.dispatchEvent(new Event("movein"));
        }
    }

    connectedCallback(): void {
        super.connectedCallback();

        this.removeMoveIn = on(
            this,
            "movein" as keyof HTMLElementEventMap,
            () => this.parentElement?.dispatchEvent(new Event("moveinstart")),
        );
    }
}

export class FrameEnd extends FrameHandle {
    static tagName = "frame-end";
    placement: Placement;

    constructor() {
        super();
        this.placement = "afterend";
    }

    getFrameRange(): Range {
        const range = new Range();
        range.setEndAfter(this);

        const maybeFramed = this.previousElementSibling;

        if (maybeFramed?.matches(this.frames ?? ":not(*)")) {
            const maybeHandleStart = maybeFramed.previousElementSibling;

            range.setEndAfter(
                maybeHandleStart?.tagName.toLowerCase() === FrameEnd.tagName
                    ? maybeHandleStart
                    : maybeFramed,
            );
        } else {
            range.setStartBefore(this);
        }

        return range;
    }

    notifyMoveIn(offset: number): void {
        if (offset === 0) {
            this.dispatchEvent(new Event("movein"));
        }
    }

    connectedCallback(): void {
        super.connectedCallback();

        this.removeMoveIn = on(
            this,
            "movein" as keyof HTMLElementEventMap,
            () => this.parentElement?.dispatchEvent(new Event("moveinend")),
        );
    }
}

function checkWhetherMovingIntoHandle(selection: Selection, handle: FrameHandle): void {
    if (selection.anchorNode === handle.firstChild && isSelectionCollapsed(selection)) {
        handle.notifyMoveIn(selection.anchorOffset);
    }
}

function checkWhetherSelectingHandle(selection: Selection, handle: FrameHandle): void {
    handle.partiallySelected = handle.firstChild && !isSelectionCollapsed(selection)
        ? selection.containsNode(handle.firstChild)
        : false;
}

export function checkHandles(): void {
    for (const handle of handles) {
        const selection = getSelection(handle)!;

        if (selection.rangeCount === 0) {
            continue;
        }

        checkWhetherMovingIntoHandle(selection, handle);
        checkWhetherSelectingHandle(selection, handle);
    }
}
