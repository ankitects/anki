// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

export class EditableContainer extends HTMLDivElement {
    baseStyle: HTMLStyleElement;
    baseRule?: CSSStyleRule;
    imageStyle?: HTMLStyleElement;

    constructor() {
        super();
        const shadow = this.attachShadow({ mode: "open" });

        if (document.documentElement.classList.contains("night-mode")) {
            this.classList.add("night-mode");
        }

        const rootStyle = document.createElement("link");
        rootStyle.setAttribute("rel", "stylesheet");
        rootStyle.setAttribute("href", "./_anki/css/editable.css");
        shadow.appendChild(rootStyle);

        this.baseStyle = document.createElement("style");
        this.baseStyle.setAttribute("rel", "stylesheet");
        this.baseStyle.id = "baseStyle";
        shadow.appendChild(this.baseStyle);
    }

    connectedCallback(): void {
        const sheet = this.baseStyle.sheet as CSSStyleSheet;
        const baseIndex = sheet.insertRule("anki-editable {}");
        this.baseRule = sheet.cssRules[baseIndex] as CSSStyleRule;
    }

    initialize(color: string): void {
        this.setBaseColor(color);
    }

    setBaseColor(color: string): void {
        if (this.baseRule) {
            this.baseRule.style.color = color;
        }
    }

    setBaseStyling(fontFamily: string, fontSize: string, direction: string): void {
        if (this.baseRule) {
            this.baseRule.style.fontFamily = fontFamily;
            this.baseRule.style.fontSize = fontSize;
            this.baseRule.style.direction = direction;
        }
    }

    isRightToLeft(): boolean {
        return this.baseRule!.style.direction === "rtl";
    }
}
