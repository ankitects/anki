// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getSelection, isSelectionCollapsed } from "@tslib/cross-browser";
import { elementIsBlock, hasBlockAttribute, nodeIsElement, nodeIsText } from "@tslib/dom";
import { on } from "@tslib/events";

import { moveChildOutOfElement } from "$lib/domlib/move-nodes";
import { placeCaretAfter, placeCaretBefore } from "$lib/domlib/place-caret";

import type { FrameHandle } from "./frame-handle";
import { checkHandles, frameElementTagName, FrameEnd, FrameStart, isFrameHandle } from "./frame-handle";

function restoreFrameHandles(mutations: MutationRecord[]): void {
    let referenceNode: Node | null = null;

    for (const mutation of mutations) {
        const frameElement = mutation.target as FrameElement;
        const framed = frameElement.querySelector(frameElement.frames!) as HTMLElement;

        if (!framed) {
            frameElement.remove();
            continue;
        }

        for (const node of mutation.addedNodes) {
            if (node === framed || isFrameHandle(node)) {
                continue;
            }

            // In some rare cases, nodes might be inserted into the frame itself.
            // For example after using execCommand.
            const placement = framed.compareDocumentPosition(node);

            if (placement & Node.DOCUMENT_POSITION_PRECEDING) {
                referenceNode = moveChildOutOfElement(
                    frameElement,
                    node,
                    "beforebegin",
                );
            } else if (placement & Node.DOCUMENT_POSITION_FOLLOWING) {
                referenceNode = moveChildOutOfElement(frameElement, node, "afterend");
            }
        }

        for (const node of mutation.removedNodes) {
            if (!isFrameHandle(node)) {
                continue;
            }

            if (
                /* avoid triggering when (un)mounting whole frame */
                mutations.length === 1
                && !node.partiallySelected
            ) {
                // Similar to a "movein", this could be considered a
                // "deletein" event and could get some special treatment, e.g.
                // first highlight the entire frame-element.
                frameElement.remove();
                continue;
            }

            if (frameElement.isConnected) {
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
    static tagName = frameElementTagName;

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
                this.refreshHandles();
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

    removeStart?: () => void;
    removeEnd?: () => void;

    addEventListeners(): void {
        this.removeStart = on(
            this,
            "moveinstart" as keyof HTMLElementEventMap,
            () => this.framedElement?.dispatchEvent(new Event("moveinstart")),
        );

        this.removeEnd = on(
            this,
            "moveinend" as keyof HTMLElementEventMap,
            () => this.framedElement?.dispatchEvent(new Event("moveinend")),
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
            const focus = previous
                    && (nodeIsText(previous)
                        || (nodeIsElement(previous) && !elementIsBlock(previous)))
                ? previous
                : this.insertAdjacentElement(
                    "beforebegin",
                    document.createElement("br"),
                );

            placeCaretAfter(focus ?? this);
        } else if (offset === 1) {
            const next = this.nextSibling;

            const focus = next
                    && (nodeIsText(next) || (nodeIsElement(next) && !elementIsBlock(next)))
                ? next
                : this.insertAdjacentElement("afterend", lineBreak);

            placeCaretBefore(focus ?? this);
        }
    }
}

function checkIfInsertingLineBreakAdjacentToBlockFrame() {
    for (const frame of frameElements) {
        if (!frame.block) {
            continue;
        }

        const selection = getSelection(frame)!;

        if (
            selection.anchorNode === frame.framedElement
            && isSelectionCollapsed(selection)
        ) {
            frame.insertLineBreak(selection.anchorOffset);
        }
    }
}

function onSelectionChange() {
    checkHandles();
    checkIfInsertingLineBreakAdjacentToBlockFrame();
}

document.addEventListener("selectionchange", onSelectionChange);

/**
 * This function wraps an element into a "frame", which looks like this:
 * <anki-frame>
 *     <frame-handle-start> </frame-handle-start>
 *     <your-element ... />
 *     <frame-handle-end> </frame-handle-end>
 * </anki-frame>
 */
export function frameElement(element: HTMLElement, block: boolean): FrameElement {
    const frame = document.createElement(FrameElement.tagName) as FrameElement;
    frame.setAttribute("block", String(block));
    frame.dataset.frames = element.tagName.toLowerCase();

    const range = new Range();
    range.selectNode(element);
    range.surroundContents(frame);

    return frame;
}
