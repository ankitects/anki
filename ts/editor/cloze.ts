// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

function autoresizeInput(input: HTMLInputElement) {
    input.style.width = "0";
    while (input.style.width !== `${input.scrollWidth}px`) {
        console.log("autoresize:", input.style.width, "to", `${input.scrollWidth}px`);
        input.style.width = `${input.scrollWidth}px`;
    }
}

function noEmpty(input: HTMLInputElement) {
    if (input.value.length === 0) {
        input.value = "1";
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
        noEmpty(this);
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

export class Cloze extends HTMLElement {
    input: ClozeNumberInput;
    open: HTMLSpanElement;
    close: HTMLSpanElement;

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
        this.prepend(this.open);

        this.close = document.createElement("span");
        this.close.textContent = "}}";
        this.close.setAttribute("contenteditable", "false");
        this.append(this.close);
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
}
