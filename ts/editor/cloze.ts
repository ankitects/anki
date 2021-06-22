// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

function autoresizeInput(input: HTMLInputElement) {
    input.style.width = "0";
    while (input.style.width !== `${input.scrollWidth}px`) {
        input.style.width = `${input.scrollWidth}px`;
    }
}

function validateInput(input: HTMLInputElement) {
    if (input.value.length === 0) {
        input.value = "1";
    } else {
        const numeric = Number(input.value);

        if (isNaN(numeric) || numeric < 1) {
            input.value = "1";
        } else if (numeric > 499) {
            input.value = "499";
        }
    }
}

function respondToVisibility(element: HTMLElement, callback: () => void) {
    const options = {
        root: document.documentElement,
    };
    const observer = new IntersectionObserver(
        (entries) =>
            entries.forEach((entry) => {
                if (entry.intersectionRatio > 0) {
                    callback();
                }
            }),
        options
    );
    observer.observe(element);
}

export class ClozeNumberInput extends HTMLInputElement {
    constructor() {
        super();
        this.type = "number";
        this.tabIndex = -1;
        this.min = "1";
        this.max = "499";

        this.className = "anki-cloze-number-input";

        this.onInput = this.onInput.bind(this);
        this.onChange = this.onChange.bind(this);

        respondToVisibility(this, () => autoresizeInput(this));
    }

    onChange(): void {
        validateInput(this);
    }

    onInput(): void {
        autoresizeInput(this);
    }

    connectedCallback(): void {
        this.addEventListener("change", this.onChange);
        this.addEventListener("input", this.onInput);
    }

    disconnectedCallback(): void {
        this.removeEventListener("change", this.onChange);
        this.removeEventListener("input", this.onInput);
    }
}

customElements.define("anki-cloze-number-input", ClozeNumberInput, {
    extends: "input",
});

const observationConfig = { childList: true };

function getLastPossiblePosition(node: Node): [Node, number] {
    switch (node.nodeType) {
        case Node.ELEMENT_NODE:
            return node.lastChild ? getLastPossiblePosition(node.lastChild) : [node, 0];
        case Node.COMMENT_NODE:
        case Node.TEXT_NODE:
        default:
            return [node, node.textContent?.length || 0];
    }
}

export class Cloze extends HTMLElement {
    observer: MutationObserver;

    input: ClozeNumberInput;
    open: HTMLSpanElement;
    close: HTMLSpanElement;

    constructor() {
        super();
        this.open = this.constructOpen();
        this.input = this.open.children[0] as ClozeNumberInput;
        this.close = this.constructClose();

        this.removeOnDelimiterDeletion = this.removeOnDelimiterDeletion.bind(this);
        this.updateCardValue = this.updateCardValue.bind(this);

        this.observer = new MutationObserver(this.removeOnDelimiterDeletion);
    }

    constructOpen(): HTMLSpanElement {
        const alreadyOpen = this.querySelector(
            '[data-cloze="open"]'
        ) as HTMLSpanElement;

        if (alreadyOpen) {
            return alreadyOpen;
        }

        const open = document.createElement("span");
        open.setAttribute("contenteditable", "false");
        open.dataset.cloze = "open";

        const prefix = document.createTextNode("{{c");
        open.append(prefix);

        const input = document.createElement("input", {
            is: "anki-cloze-number-input",
        }) as ClozeNumberInput;
        open.append(input);

        const infix = document.createTextNode("::");
        open.appendChild(infix);

        return open;
    }

    constructClose(): HTMLSpanElement {
        const alreadyClose = this.querySelector(
            '[data-cloze="close"]'
        ) as HTMLSpanElement;

        if (alreadyClose) {
            return alreadyClose;
        }

        const close = document.createElement("span");
        close.textContent = "}}";
        close.setAttribute("contenteditable", "false");
        close.dataset.cloze = "close";

        return close;
    }

    static get observedAttributes() {
        return ["card"];
    }

    attributeChangedCallback(name: string, _oldValue: string, newValue: string): void {
        switch (name) {
            case "card":
                this.input.value = newValue;
                break;
        }
    }

    connectedCallback(): void {
        this.decorate();
        this.observe();

        this.input.addEventListener("change", this.updateCardValue);
    }

    disconnectedCallback(): void {
        this.disconnect();

        this.input.removeEventListener("change", this.updateCardValue);
    }

    updateCardValue(): void {
        this.setAttribute("card", this.input.value);
    }

    decorate(): void {
        this.prepend(this.open);
        this.append(this.close);
    }

    cleanup(): void {
        this.removeChild(this.open);
        this.removeChild(this.close);
    }

    observe(): void {
        this.observer.observe(this, observationConfig);
    }

    disconnect(): void {
        this.observer.disconnect();
    }

    removeOnDelimiterDeletion(mutations: MutationRecord[]): void {
        let openRemoved = false;
        let closeRemoved = false;

        for (const mutation of mutations) {
            openRemoved =
                openRemoved ||
                Array.prototype.includes.call(mutation.removedNodes, this.open);
            closeRemoved =
                closeRemoved ||
                Array.prototype.includes.call(mutation.removedNodes, this.close);
        }

        if (openRemoved || closeRemoved) {
            const parent = this.parentElement;
            const clozed = Array.prototype.slice.call(
                this.childNodes,
                openRemoved ? 0 : 1,
                closeRemoved ? this.childNodes.length : -1
            );

            let movePosition: [Node, number] | null = null;
            if (closeRemoved && !openRemoved && clozed.length > 0) {
                movePosition = getLastPossiblePosition(clozed[clozed.length - 1]);
            }

            this.replaceWith(...clozed);

            if (parent && movePosition) {
                // place caret at the end of the inner text
                const range = new Range();
                range.setStart(...movePosition);
                range.collapse(true);

                const selection = (
                    parent.getRootNode() as ShadowRoot | Document
                ).getSelection()!;
                selection.removeAllRanges();
                selection.addRange(range);
            }

            // anki-cloze could have been removed as well in the meantime
            parent?.normalize();
        }
    }
}
