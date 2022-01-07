// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import {
    nodeIsText,
    nodeIsElement,
    elementIsEmpty,
    elementIsBlock,
    hasBlockAttribute,
} from "../lib/dom";
import { on } from "../lib/events";
import { getSelection } from "../lib/cross-browser";
import { moveChildOutOfElement } from "../domlib/move-nodes";
import { placeCaretBefore, placeCaretAfter } from "../domlib/place-caret";

/**
 * I originally used a zero width space, however, in contentEditable, if
 * a line ends in a zero width space, and you click _after_ the line,
 * the caret will be placed _before_ the zero width space.
 * Instead I use a hairline space.
 */
const spaceCharacter = "\u200a";
const spaceRegex = /[\u200a]/g;

function isFrameHandle(node: unknown): node is FrameHandle {
    return node instanceof FrameHandle;
}

function skippableNode(handleElement: FrameHandle, node: Node): boolean {
    /**
     * We only want to move nodes, which are direct descendants of the FrameHandle
     * MutationRecords however might include nodes which were directly removed again
     */
    return (
        (nodeIsText(node) &&
            (node.data === spaceCharacter || node.data.length === 0)) ||
        !Array.prototype.includes.call(handleElement.childNodes, node)
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
            const placement =
                handleElement instanceof FrameStart ? "beforebegin" : "afterend";
            const frameElement = handleElement.parentElement as FrameElement;

            for (const node of mutation.addedNodes) {
                if (skippableNode(handleElement, node)) {
                    continue;
                }

                if (
                    nodeIsElement(node) &&
                    !elementIsEmpty(node) &&
                    (node.textContent === spaceCharacter ||
                        node.textContent?.length === 0)
                ) {
                    /**
                     * When we surround the spaceCharacter of the frame handle
                     */
                    node.replaceWith(new Text(spaceCharacter));
                } else {
                    referenceNode = moveChildOutOfElement(
                        frameElement,
                        node,
                        placement,
                    );
                }
            }
        } else if (mutation.type === "characterData") {
            if (
                !nodeIsText(target) ||
                !isFrameHandle(target.parentElement) ||
                skippableNode(target.parentElement, target)
            ) {
                continue;
            }

            const handleElement = target.parentElement;
            const placement =
                handleElement instanceof FrameStart ? "beforebegin" : "afterend";
            const frameElement = handleElement.parentElement! as FrameElement;

            const cleaned = target.data.replace(spaceRegex, "");
            const text = new Text(cleaned);

            if (placement === "beforebegin") {
                frameElement.before(text);
            } else {
                frameElement.after(text);
            }

            handleElement.refreshSpace();
            referenceNode = text;
        }
    }

    if (referenceNode) {
        placeCaretAfter(referenceNode);
    }
}

const handleObserver = new MutationObserver(restoreHandleContent);
const handles: Set<FrameHandle> = new Set();

abstract class FrameHandle extends HTMLElement {
    static get observedAttributes(): string[] {
        return ["data-frames"];
    }

    frames?: string;

    constructor() {
        super();
        handleObserver.observe(this, {
            childList: true,
            subtree: true,
            characterData: true,
        });
    }

    attributeChangedCallback(name: string, old: string, newValue: string) {
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
            !this.firstChild ||
            !(nodeIsText(this.firstChild) && this.firstChild.data === spaceCharacter)
        );
    }

    refreshSpace(): void {
        while (this.firstChild) {
            this.removeChild(this.firstChild);
        }

        this.append(new Text(spaceCharacter));
    }

    hostedUnderFrame(): boolean {
        return this.parentElement!.tagName === FrameElement.tagName.toUpperCase();
    }

    connectedCallback(): void {
        if (this.invalidSpace()) {
            this.refreshSpace();
        }

        if (!this.hostedUnderFrame()) {
            const range = this.getFrameRange();

            const frameElement = document.createElement(
                FrameElement.tagName,
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
    }

    abstract notifyMoveIn(offset: number): void;
}

export class FrameStart extends FrameHandle {
    static tagName = "frame-start";

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

        this.removeMoveIn = on(this, "movein" as keyof HTMLElementEventMap, () =>
            this.parentElement?.dispatchEvent(new Event("moveinstart")),
        );
    }
}

export class FrameEnd extends FrameHandle {
    static tagName = "frame-end";

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

        this.removeMoveIn = on(this, "movein" as keyof HTMLElementEventMap, () =>
            this.parentElement?.dispatchEvent(new Event("moveinend")),
        );
    }
}

function restoreFrameHandles(mutations: MutationRecord[]): void {
    let referenceNode: Node | null = null;

    for (const mutation of mutations) {
        const frameElement = mutation.target as FrameElement;
        const framed = frameElement.querySelector(frameElement.frames!) as HTMLElement;

        for (const node of mutation.addedNodes) {
            if (node === framed || isFrameHandle(node)) {
                continue;
            }

            /**
             * In some rare cases, nodes might be inserted into the frame itself.
             * For example after using execCommand.
             */
            const placement = node.compareDocumentPosition(framed);

            if (placement & Node.DOCUMENT_POSITION_FOLLOWING) {
                referenceNode = moveChildOutOfElement(frameElement, node, "afterend");
                continue;
            } else if (placement & Node.DOCUMENT_POSITION_PRECEDING) {
                referenceNode = moveChildOutOfElement(
                    frameElement,
                    node,
                    "beforebegin",
                );
                continue;
            }
        }

        for (const node of mutation.removedNodes) {
            if (
                /* avoid triggering when (un)mounting whole frame */
                mutations.length === 1 &&
                nodeIsElement(node) &&
                isFrameHandle(node)
            ) {
                /* When deleting from _outer_ position in FrameHandle to _inner_ position */
                frameElement.remove();
                continue;
            }

            if (
                nodeIsElement(node) &&
                isFrameHandle(node) &&
                frameElement.isConnected &&
                !frameElement.block
            ) {
                frameElement.refreshHandles();
                continue;
            }
        }
    }

    if (referenceNode) {
        placeCaretAfter(referenceNode);
    }
}

const frameObserver = new MutationObserver(restoreFrameHandles);
const frameElements = new Set<FrameElement>();

export class FrameElement extends HTMLElement {
    static tagName = "anki-frame";

    static get observedAttributes(): string[] {
        return ["data-frames", "block"];
    }

    get framedElement(): HTMLElement | null {
        return this.frames ? this.querySelector(this.frames) : null;
    }

    frames?: string;
    block: boolean;

    handleStart?: FrameStart;
    handleEnd?: FrameEnd;

    constructor() {
        super();
        this.block = hasBlockAttribute(this);
        frameObserver.observe(this, { childList: true });
    }

    attributeChangedCallback(name: string, old: string, newValue: string): void {
        if (newValue === old) {
            return;
        }

        switch (name) {
            case "data-frames":
                this.frames = newValue;

                if (!this.framedElement) {
                    this.remove();
                    return;
                }
                break;

            case "block":
                this.block = newValue !== "false";

                if (!this.block) {
                    this.refreshHandles();
                } else {
                    this.removeHandles();
                }

                break;
        }
    }

    getHandleFrom(node: Element | null, start: boolean): FrameHandle {
        const handle = isFrameHandle(node)
            ? node
            : (document.createElement(
                  start ? FrameStart.tagName : FrameEnd.tagName,
              ) as FrameHandle);

        handle.dataset.frames = this.frames;

        return handle;
    }

    refreshHandles(): void {
        customElements.upgrade(this);

        this.handleStart = this.getHandleFrom(this.firstElementChild, true);
        this.handleEnd = this.getHandleFrom(this.lastElementChild, false);

        if (!this.handleStart.isConnected) {
            this.prepend(this.handleStart);
        }

        if (!this.handleEnd.isConnected) {
            this.append(this.handleEnd);
        }
    }

    removeHandles(): void {
        this.handleStart?.remove();
        this.handleStart = undefined;

        this.handleEnd?.remove();
        this.handleEnd = undefined;
    }

    removeStart?: () => void;
    removeEnd?: () => void;

    addEventListeners(): void {
        this.removeStart = on(this, "moveinstart" as keyof HTMLElementEventMap, () =>
            this.framedElement?.dispatchEvent(new Event("moveinstart")),
        );

        this.removeEnd = on(this, "moveinend" as keyof HTMLElementEventMap, () =>
            this.framedElement?.dispatchEvent(new Event("moveinend")),
        );
    }

    removeEventListeners(): void {
        this.removeStart?.();
        this.removeStart = undefined;

        this.removeEnd?.();
        this.removeEnd = undefined;
    }

    connectedCallback(): void {
        frameElements.add(this);
        this.addEventListeners();
    }

    disconnectedCallback(): void {
        frameElements.delete(this);
        this.removeEventListeners();
    }

    insertLineBreak(offset: number): void {
        const lineBreak = document.createElement("br");

        if (offset === 0) {
            const previous = this.previousSibling;
            const focus =
                previous &&
                (nodeIsText(previous) ||
                    (nodeIsElement(previous) && !elementIsBlock(previous)))
                    ? previous
                    : this.insertAdjacentElement(
                          "beforebegin",
                          document.createElement("br"),
                      );

            placeCaretAfter(focus ?? this);
        } else if (offset === 1) {
            const next = this.nextSibling;

            const focus =
                next &&
                (nodeIsText(next) || (nodeIsElement(next) && !elementIsBlock(next)))
                    ? next
                    : this.insertAdjacentElement("afterend", lineBreak);

            placeCaretBefore(focus ?? this);
        }
    }
}

function checkWhetherMovingIntoHandle() {
    for (const handle of handles) {
        const selection = getSelection(handle)!;

        if (selection.anchorNode === handle.firstChild && selection.isCollapsed) {
            handle.notifyMoveIn(selection.anchorOffset);
        }
    }
}

function checkIfInsertingLineBreakAdjacentToBlockFrame() {
    for (const frame of frameElements) {
        if (!frame.block) {
            continue;
        }

        const selection = getSelection(frame)!;

        if (selection.anchorNode === frame.framedElement && selection.isCollapsed) {
            frame.insertLineBreak(selection.anchorOffset);
        }
    }
}

function onSelectionChange() {
    checkWhetherMovingIntoHandle();
    checkIfInsertingLineBreakAdjacentToBlockFrame();
}

document.addEventListener("selectionchange", onSelectionChange);

export function frameElement(element: HTMLElement, block: boolean): FrameElement {
    const frame = document.createElement(FrameElement.tagName) as FrameElement;
    frame.setAttribute("block", String(block));
    frame.dataset.frames = element.tagName.toLowerCase();

    const range = new Range();
    range.selectNode(element);
    range.surroundContents(frame);

    return frame;
}
