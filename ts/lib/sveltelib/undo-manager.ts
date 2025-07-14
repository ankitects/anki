import { getRange, getSelection } from "@tslib/cross-browser";

type State = {
    content: string;
    position: number;
}

export class UndoManager {
    private stack: State[] = [];
    private isUpdating: boolean = false;
    private lastPosition: number = 0;
    public register = this.debounce(this.push, 700, (position: number) => this.lastPosition = position);

    private push(element: Element): void {
        if(this.isUpdating) return;
        if (this.stack.length > 0 && this.stack[this.stack.length-1].content === element.innerHTML) return;

        const state = {content: element.innerHTML, position: this.lastPosition}
        this.stack.push(state);
    }

    public undo(element: Element): void{
        this.isUpdating = true;
        this.stack.pop();

        let last: State;
        if(this.stack.length <= 0) last = {content: "", position: this.lastPosition};
        else last = this.stack[this.stack.length-1];
        element.innerHTML = last.content;

        const selection = getSelection(element)!;
        let range = getRange(selection);

        let counter = this.lastPosition;
        let nodeFound: Node | null = null;
        let nodeOffset = 0;
        for(const node of element.childNodes){
            let nodeLength = node.textContent?.length || 0;
            if (counter <= nodeLength) {
                nodeFound = node;
                nodeOffset = counter;
                break;
            }
            if(node.nodeType !== Node.TEXT_NODE) counter--;
            counter -= nodeLength;
        }
        if(!range){
            this.isUpdating = false;
            return;
        }
        if(!nodeFound){
            if(element.lastChild) range?.setStart(element.lastChild as Node, (element.lastChild?.textContent?.length || 0))
            range.collapse(true);
            selection.removeAllRanges()
            selection.addRange(range);
            this.isUpdating = false;
            return;
        }
        let finalOffset = Math.min(nodeOffset, nodeFound.textContent?.length || 0);
        range.setStart(nodeFound, finalOffset);
        range.collapse(true);
        selection.removeAllRanges()
        selection.addRange(range);

        if(this.stack.length > 0) this.lastPosition = this.stack[this.stack.length-1].position;
        this.isUpdating = false;
    }

    private debounce(func: Function, delay: number, onTransactionStart: Function): Function {
        let timeout;
        return (...args) => {
            const isNewTransaction = timeout === undefined;
            clearTimeout(timeout);
            if(isNewTransaction) onTransactionStart.call(this, args[1]);

            timeout = setTimeout(() => {
                func.call(this, args[0]);
                timeout = undefined;
            }, delay);
        };
    }
}