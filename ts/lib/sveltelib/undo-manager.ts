// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { getRange, getSelection } from "@tslib/cross-browser";
import { getMaxOffset } from "./input-handler";

type State = {
    content: string;
    position: number;
};

export class UndoManager {
    private undoStack: State[] = [];
    private redoStack: State[] = [];
    private isUpdating = false;
    private transactionStart = 0;
    public register = this.debounce(this.pushToUndo, 500, (position: number) => this.transactionStart = position);

    public clearRedoStack() {
        if (this.isUpdating) { return; }
        this.redoStack = [];
    }

    public pushToUndo(content: string): void {
        if (this.isUpdating) { return; }
        if (this.undoStack.length > 0 && this.undoStack[this.undoStack.length - 1].content === content) { return; }

        const state = { content, position: this.transactionStart };
        this.undoStack.push(state);
    }

    private pushToRedo(content: string): void {
        if (this.redoStack.length > 0 && this.redoStack[this.redoStack.length - 1].content === content) { return; }

        const state = { content: content, position: this.transactionStart };
        this.redoStack.push(state);
    }

    public undo(element: Element): void {
        this.isUpdating = true;
        const undoedState = this.undoStack.pop();
        if (undoedState) { this.pushToRedo(undoedState.content); }

        let last: State;
        if (this.undoStack.length <= 0) { last = { content: "", position: 0 }; }
        else { last = this.undoStack[this.undoStack.length - 1]; }
        element.innerHTML = last.content;

        const selection = getSelection(element)!;
        const range = getRange(selection);

        let counter = this.transactionStart;
        let nodeFound: Node | null = null;
        let nodeOffset = 0;
        for (const node of element.childNodes) {
            const nodeLength = node.textContent?.length || 0;
            if (counter <= nodeLength) {
                nodeFound = node;
                nodeOffset = counter;
                break;
            }
            if (node.nodeType !== Node.TEXT_NODE) { counter--; }
            counter -= nodeLength;
        }
        if (!range) {
            this.isUpdating = false;
            return;
        }
        if (!nodeFound) {
            if (element.lastChild) {
                range?.setStart(element.lastChild as Node, element.lastChild?.textContent?.length || 0);
            }
            range.collapse(true);
            selection.removeAllRanges();
            selection.addRange(range);
            this.isUpdating = false;
            return;
        }
        let finalOffset = Math.min(nodeOffset, nodeFound.textContent?.length || 0);
        if (finalOffset > getMaxOffset(nodeFound)) { finalOffset = getMaxOffset(nodeFound); }
        range.setStart(nodeFound, finalOffset);
        range.collapse(true);
        selection.removeAllRanges();
        selection.addRange(range);

        if (this.undoStack.length > 0) { this.transactionStart = this.undoStack[this.undoStack.length - 1].position; }
        this.isUpdating = false;
    }

    public redo(element: Element): void {
        const redoedState = this.redoStack.pop();
        if (!redoedState) { return; }
        this.transactionStart = redoedState.position;
        this.pushToUndo(redoedState.content);

        this.isUpdating = true;
        element.innerHTML = redoedState.content;

        const selection = getSelection(element)!;
        const range = getRange(selection);

        let counter = this.transactionStart;
        let nodeFound: Node | null = null;
        let nodeOffset = 0;
        for (const node of element.childNodes) {
            const nodeLength = node.textContent?.length || 0;
            if (counter <= nodeLength) {
                nodeFound = node;
                nodeOffset = counter;
                break;
            }
            if (node.nodeName === "BR") { counter--; }
            counter -= nodeLength;
        }
        if (!range) {
            this.isUpdating = false;
            return;
        }
        if (!nodeFound) {
            if (element.lastChild) {
                range?.setStart(element.lastChild as Node, element.lastChild?.textContent?.length || 0);
            }
            range.collapse(true);
            selection.removeAllRanges();
            selection.addRange(range);
            this.isUpdating = false;
            return;
        }
        let finalOffset = Math.min(nodeOffset, nodeFound.textContent?.length || 0);
        if (finalOffset > getMaxOffset(nodeFound)) { finalOffset = getMaxOffset(nodeFound); }
        range.setStart(nodeFound, finalOffset);
        range.collapse(true);
        selection.removeAllRanges();
        selection.addRange(range);

        if (this.redoStack.length > 0) { this.transactionStart = this.redoStack[this.redoStack.length - 1].position; }
        this.isUpdating = false;
    }

    private debounce<A, B>(
        func: (arg: A) => void,
        delay: number,
        onTransactionStart: (arg: B) => void,
    ): (mainArg: A, startArg: B) => void {
        let timeout: ReturnType<typeof setTimeout> | undefined;

        return (mainArg: A, startArg: B) => {
            const isNewTransaction = timeout === undefined;
            clearTimeout(timeout);

            if (isNewTransaction) {
                onTransactionStart.call(this, startArg);
            }

            timeout = setTimeout(() => {
                func.call(this, mainArg);
                timeout = undefined;
            }, delay);
        };
    }
}
