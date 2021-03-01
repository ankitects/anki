import { bridgeCommand } from "./lib";

export class LabelContainer extends HTMLDivElement {
    sticky: HTMLSpanElement;
    label: HTMLSpanElement;

    constructor() {
        super();
        this.className = "d-flex justify-content-between";

        this.label = document.createElement("span");
        this.label.className = "fieldname";
        this.appendChild(this.label);

        this.sticky = document.createElement("span");
        this.sticky.className = "bi me-1 sticky-icon";
        this.sticky.hidden = true;
        this.appendChild(this.sticky);

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

    setSticky(state: boolean): void {
        this.sticky.classList.toggle("bi-pin-angle-fill", state);
        this.sticky.classList.toggle("bi-pin-angle", !state);
    }

    activateSticky(initialState: boolean): void {
        this.setSticky(initialState);
        this.sticky.hidden = false;
    }

    toggleSticky(): void {
        bridgeCommand(
            `toggleSticky:${this.getAttribute("ord")}`,
            (newState: boolean): void => {
                this.setSticky(newState);
            }
        );
    }
}
