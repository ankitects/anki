import { bridgeCommand } from "./lib";

export class LabelContainer extends HTMLDivElement {
    sticky: HTMLSpanElement;
    label: HTMLSpanElement;

    constructor() {
        super();
        this.className = "d-flex";

        this.sticky = document.createElement("span");
        this.sticky.className = "bi bi-pin-angle-fill me-1 sticky-icon";
        this.sticky.hidden = true;
        this.appendChild(this.sticky);

        this.label = document.createElement("span");
        this.label.className = "fieldname";
        this.appendChild(this.label);

        this.toggleSticky = this.toggleSticky.bind(this);
    }

    connectedCallback(): void {
        this.sticky.addEventListener("click", this.toggleSticky);
    }

    disconnectedCallback(): void {
        this.sticky.removeEventListener("click", this.toggleSticky);
    }

    initialize(labelName: string): void {
        this.label.innerText = labelName;
    }

    activateSticky(initialState: boolean): void {
        this.sticky.classList.toggle("is-active", initialState);
        this.sticky.hidden = false;
    }

    toggleSticky(): void {
        bridgeCommand(`toggleSticky:${this.getAttribute("ord")}`, () => {
            this.sticky.classList.toggle("is-active");
        });
    }
}
