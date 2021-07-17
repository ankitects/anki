// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { bridgeCommand } from "./lib";
import pinIcon from "./pin-angle.svg";
import gripIcon from "./grip-vertical.svg";

function removeHoverIcon(evt: Event): void {
    const icon = evt.currentTarget as HTMLElement;
    icon.classList.remove("icon--hover");
}

function hoverIcon(evt: Event): void {
    const icon = evt.currentTarget as HTMLElement;
    icon.classList.add("icon--hover");
}

function setFieldDraggable(evt: Event): void {
    const icon = evt.currentTarget as HTMLElement;

    // TODO make this event-based when moving to Svelte
    const field = icon.parentElement!.parentElement!;
    field.draggable = true;

    let dragged: HTMLElement;

    document.addEventListener("dragstart", (event) => {
        dragged = event.target as HTMLElement;
        dragged.classList.add("is-dragged");
    });

    document.addEventListener("dragend", () => {
        dragged.classList.remove("is-dragged");
        dragged.draggable = false;
    });
}

export class LabelContainer extends HTMLDivElement {
    sticky: HTMLSpanElement;
    grip: HTMLSpanElement;
    label: HTMLSpanElement;

    constructor() {
        super();
        this.className = "fname d-flex justify-content-between";

        this.label = document.createElement("span");
        this.label.className = "fieldname";
        this.appendChild(this.label);

        const spacer = document.createElement("span");
        spacer.className = "flex-grow-1";
        this.appendChild(spacer);

        this.sticky = document.createElement("span");
        this.sticky.className = "icon icon-toggle ms-1";
        this.sticky.innerHTML = pinIcon;
        this.sticky.hidden = true;
        this.appendChild(this.sticky);

        this.grip = document.createElement("span");
        this.grip.className = "icon ms-1";
        this.grip.innerHTML = gripIcon;
        this.appendChild(this.grip);

        this.toggleSticky = this.toggleSticky.bind(this);
    }

    connectedCallback(): void {
        this.sticky.addEventListener("click", this.toggleSticky);
        this.sticky.addEventListener("mouseenter", hoverIcon);
        this.sticky.addEventListener("mouseleave", removeHoverIcon);
        this.grip.addEventListener("mousedown", setFieldDraggable);
        this.grip.addEventListener("mouseenter", hoverIcon);
        this.grip.addEventListener("mouseleave", removeHoverIcon);
    }

    disconnectedCallback(): void {
        this.sticky.removeEventListener("click", this.toggleSticky);
        this.sticky.removeEventListener("mouseenter", hoverIcon);
        this.sticky.removeEventListener("mouseleave", removeHoverIcon);
        this.grip.removeEventListener("mousedown", setFieldDraggable);
        this.grip.removeEventListener("mouseenter", hoverIcon);
        this.grip.removeEventListener("mouseleave", removeHoverIcon);
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
