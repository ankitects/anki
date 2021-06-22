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

export class Cloze extends HTMLElement {
    root: ShadowRoot | Document = document;
    observer: MutationObserver;

    input: ClozeNumberInput;
    open: HTMLSpanElement;
    close: HTMLSpanElement;

    restoreInput = false;

    constructor() {
        super();

        this.open = document.createElement("span");
        this.open.setAttribute("contenteditable", "false");

        const prefix = document.createTextNode("{{c");
        this.open.append(prefix);

        this.input = document.createElement("input", {
            is: "anki-cloze-number-input",
        }) as ClozeNumberInput;
        this.open.append(this.input);

        const infix = document.createTextNode("::");
        this.open.appendChild(infix);

        this.close = document.createElement("span");
        this.close.textContent = "}}";
        this.close.setAttribute("contenteditable", "false");

        this.decorate();

        this.removeOnDelimiterDeletion = this.removeOnDelimiterDeletion.bind(this);
        this.updateCardValue = this.updateCardValue.bind(this);

        this.observer = new MutationObserver(this.removeOnDelimiterDeletion);
    }

    static get observedAttributes() {
        return ["card"];
    }

    connectedCallback(): void {
        this.root = this.getRootNode() as ShadowRoot;
        const event = new CustomEvent("newcloze", {
            bubbles: true,
        });
        this.dispatchEvent(event);

        this.observe();

        this.input.addEventListener("change", this.updateCardValue);
    }

    disconnectedCallback(): void {
        this.input.removeEventListener("change", this.updateCardValue);
    }

    attributeChangedCallback(name: string, _oldValue: string, newValue: string): void {
        switch (name) {
            case "card":
                this.input.value = newValue;
                break;
        }
    }

    updateCardValue(): void {
        this.setAttribute("card", this.input.value);
    }

    decorate(): void {
        this.prepend(this.open);
        this.append(this.close);

        if (this.restoreInput) {
            this.input.focus();
        }
    }

    cleanup(): void {
        this.restoreInput = this.root.activeElement === this.input;

        if (this.restoreInput) {
            this.parentElement!.focus();
        }

        this.disconnect();
        this.removeChild(this.open);
        this.removeChild(this.close);
        this.observe();
    }

    observe(): void {
        this.observer.observe(this, observationConfig);
    }

    disconnect(): void {
        this.observer.disconnect();
    }

    removeOnDelimiterDeletion(mutations: MutationRecord[]): void {
        for (const mutation of mutations) {
            if (
                Array.prototype.includes.call(mutation.removedNodes, this.open) ||
                Array.prototype.includes.call(mutation.removedNodes, this.close)
            ) {
                const parent = this.parentElement!;
                const event = new CustomEvent("removecloze", {
                    bubbles: true,
                });
                this.dispatchEvent(event);
                this.replaceWith(...Array.prototype.slice.call(this.childNodes, 1, -1));
                parent.normalize();

                return;
            }
        }
    }
}
