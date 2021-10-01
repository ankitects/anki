// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { EditorField } from "./editor-field";
import * as tr from "../lib/i18n";

import { registerShortcut } from "../lib/shortcuts";
import { bridgeCommand } from "./lib";
import { appendInParentheses } from "./helpers";
import { saveField } from "./saving";
import { getCurrentField, forEditorField, i18n } from ".";
import pinIcon from "bootstrap-icons/icons/pin-angle.svg";

function toggleStickyCurrentField(): void {
    const currentField = getCurrentField();

    if (currentField) {
        const labelContainer = (currentField.parentElement as EditorField)
            .labelContainer;
        labelContainer.toggleSticky();
    }
}

function toggleStickyAll(): void {
    bridgeCommand("toggleStickyAll", (values: boolean[]) =>
        forEditorField(values, (field, value) => field.labelContainer.setSticky(value))
    );
}

export function activateStickyShortcuts(): void {
    registerShortcut(toggleStickyCurrentField, "F9");
    registerShortcut(toggleStickyAll, "Shift+F9");
}

export class LabelContainer extends HTMLDivElement {
    label: HTMLSpanElement;
    fieldState: HTMLSpanElement;

    sticky: HTMLSpanElement;

    constructor() {
        super();
        this.className = "fname d-flex justify-content-between";

        this.label = document.createElement("span");
        this.label.className = "fieldname";
        this.appendChild(this.label);

        this.fieldState = document.createElement("span");
        this.fieldState.className = "field-state d-flex justify-content-between";
        this.appendChild(this.fieldState);

        this.sticky = document.createElement("span");
        this.sticky.className = "icon pin-icon";
        this.sticky.innerHTML = pinIcon;
        this.sticky.hidden = true;

        i18n.then(() => {
            this.sticky.title = appendInParentheses(tr.editingToggleSticky(), "F9");
        });

        this.fieldState.appendChild(this.sticky);

        this.setSticky = this.setSticky.bind(this);
        this.hoverIcon = this.hoverIcon.bind(this);
        this.removeHoverIcon = this.removeHoverIcon.bind(this);
        this.toggleSticky = this.toggleSticky.bind(this);
        this.toggleStickyEvent = this.toggleStickyEvent.bind(this);
        this.keepFocus = this.keepFocus.bind(this);
        this.stopPropagation = this.stopPropagation.bind(this);
    }

    keepFocus(event: Event): void {
        event.preventDefault();
    }

    stopPropagation(event: Event): void {
        this.keepFocus(event);
        event.stopPropagation();
    }

    connectedCallback(): void {
        this.addEventListener("mousedown", this.keepFocus);
        this.sticky.addEventListener("mousedown", this.stopPropagation);
        this.sticky.addEventListener("click", this.toggleStickyEvent);
        this.sticky.addEventListener("mouseenter", this.hoverIcon);
        this.sticky.addEventListener("mouseleave", this.removeHoverIcon);
    }

    disconnectedCallback(): void {
        this.removeEventListener("mousedown", this.keepFocus);
        this.sticky.removeEventListener("mousedown", this.stopPropagation);
        this.sticky.removeEventListener("click", this.toggleStickyEvent);
        this.sticky.removeEventListener("mouseenter", this.hoverIcon);
        this.sticky.removeEventListener("mouseleave", this.removeHoverIcon);
    }

    initialize(labelName: string): void {
        this.label.innerText = labelName;
    }

    activateSticky(initialState: boolean): void {
        this.setSticky(initialState);
        this.sticky.hidden = false;
    }

    setSticky(state: boolean): void {
        this.sticky.classList.toggle("is-inactive", !state);
    }

    hoverIcon(): void {
        this.sticky.classList.add("icon--hover");
    }

    removeHoverIcon(): void {
        this.sticky.classList.remove("icon--hover");
    }

    toggleSticky(): void {
        saveField((this.parentElement as EditorField).editingArea, "key");
        bridgeCommand(`toggleSticky:${this.getAttribute("ord")}`, this.setSticky);
        this.removeHoverIcon();
    }

    toggleStickyEvent(event: Event): void {
        event.stopPropagation();
        this.toggleSticky();
    }
}

customElements.define("anki-label-container", LabelContainer, { extends: "div" });
