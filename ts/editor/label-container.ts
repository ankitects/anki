// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { bridgeCommand } from "./lib";
import pinIcon from "./pin-angle.svg";

function removeHoverIcon(evt: Event): void {
    const icon = evt.currentTarget as HTMLElement;
    icon.classList.remove("icon--hover");
}

function hoverIcon(evt: Event): void {
    const icon = evt.currentTarget as HTMLElement;
    icon.classList.add("icon--hover");
}

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
        this.sticky.className = "icon pin-icon me-1";
        this.sticky.innerHTML = pinIcon;
        this.sticky.hidden = true;
        this.appendChild(this.sticky);

        this.toggleSticky = this.toggleSticky.bind(this);
    }

    connectedCallback(): void {
        this.sticky.addEventListener("click", this.toggleSticky);
        this.sticky.addEventListener("mouseenter", hoverIcon);
        this.sticky.addEventListener("mouseleave", removeHoverIcon);
    }

    disconnectedCallback(): void {
        this.sticky.removeEventListener("click", this.toggleSticky);
        this.sticky.removeEventListener("mouseenter", hoverIcon);
        this.sticky.removeEventListener("mouseleave", removeHoverIcon);
    }

    initialize(labelName: string): void {
        this.label.innerText = labelName;
    }

    setSticky(state: boolean): void {
        this.sticky.classList.toggle("is-inactive", !state);
    }

    activateSticky(initialState: boolean): void {
        this.setSticky(initialState);
        this.sticky.hidden = false;
    }

    toggleSticky(evt: Event): void {
        bridgeCommand(
            `toggleSticky:${this.getAttribute("ord")}`,
            (newState: boolean): void => {
                this.setSticky(newState);
            }
        );
        removeHoverIcon(evt);
    }
}
