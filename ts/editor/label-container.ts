// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { EditorField } from "./editor-field";
import * as tr from "lib/i18n";

import { registerShortcut } from "lib/shortcuts";
import { bridgeCommand } from "./lib";
import { appendInParentheses } from "./helpers";
import { getCurrentField, forEditorField, i18n } from ".";
import pinIcon from "./pin-angle.svg";

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
    sticky: HTMLSpanElement;
    label: HTMLSpanElement;

    constructor() {
        super();
        this.className = "d-flex justify-content-between";

        i18n.then(() => {
            this.title = appendInParentheses(tr.editingToggleSticky(), "F9");
        });

        this.label = document.createElement("span");
        this.label.className = "fieldname";
        this.appendChild(this.label);

        this.sticky = document.createElement("span");
        this.sticky.className = "icon pin-icon me-1";
        this.sticky.innerHTML = pinIcon;
        this.sticky.hidden = true;
        this.appendChild(this.sticky);

        this.setSticky = this.setSticky.bind(this);
        this.hoverIcon = this.hoverIcon.bind(this);
        this.removeHoverIcon = this.removeHoverIcon.bind(this);
        this.toggleSticky = this.toggleSticky.bind(this);
    }

    connectedCallback(): void {
        this.sticky.addEventListener("click", this.toggleSticky);
        this.sticky.addEventListener("mouseenter", this.hoverIcon);
        this.sticky.addEventListener("mouseleave", this.removeHoverIcon);
    }

    disconnectedCallback(): void {
        this.sticky.removeEventListener("click", this.toggleSticky);
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
        bridgeCommand(`toggleSticky:${this.getAttribute("ord")}`, this.setSticky);
        this.removeHoverIcon();
    }
}
