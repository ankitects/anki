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

function respondToVisibility(
    element: HTMLElement,
    callback: () => void
) {
    const options = {
        root: document.documentElement,
    };
    const observer = new IntersectionObserver(
        (entries) => entries.forEach((entry) => {
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

        noEmpty(this);
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

    constructor() {
        super();
        this.input = document.createElement("input", {
            is: "anki-cloze-number-input",
        }) as ClozeNumberInput;
        this.appendChild(this.input);
    }

    connectedCallback(): void {}
}
