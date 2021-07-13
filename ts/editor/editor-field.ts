// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import type { EditingArea } from "./editing-area";
import type { LabelContainer } from "./label-container";

export class EditorField extends HTMLDivElement {
    labelContainer: LabelContainer;
    editingArea: EditingArea;

    constructor() {
        super();
        this.labelContainer = document.createElement("div", {
            is: "anki-label-container",
        }) as LabelContainer;
        this.appendChild(this.labelContainer);

        this.editingArea = document.createElement("div", {
            is: "anki-editing-area",
        }) as EditingArea;
        this.appendChild(this.editingArea);
    }

    static get observedAttributes(): string[] {
        return ["ord"];
    }

    set ord(n: number) {
        this.setAttribute("ord", String(n));
    }

    attributeChangedCallback(name: string, _oldValue: string, newValue: string): void {
        switch (name) {
            case "ord":
                this.editingArea.setAttribute("ord", newValue);
                this.labelContainer.setAttribute("ord", newValue);
        }
    }

    initialize(label: string, color: string, content: string): void {
        this.labelContainer.initialize(label);
        this.editingArea.initialize(color, content);
    }

    setBaseStyling(fontFamily: string, fontSize: string, direction: string): void {
        this.editingArea.setBaseStyling(fontFamily, fontSize, direction);
    }
}
