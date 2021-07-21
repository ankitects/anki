// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

export class EditableContainer extends HTMLDivElement {
    baseStyle: HTMLStyleElement;
    baseRule?: CSSStyleRule;
    imageRule?: CSSStyleRule;

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
        shadow.appendChild(this.baseStyle);
    }

    connectedCallback(): void {
        const sheet = this.baseStyle.sheet as CSSStyleSheet;
        const baseIndex = sheet.insertRule("anki-editable {}");
        this.baseRule = sheet.cssRules[baseIndex] as CSSStyleRule;

        const imageIndex = sheet.insertRule("anki-editable img {}");
        this.imageRule = sheet.cssRules[imageIndex] as CSSStyleRule;
        this.imageRule.style.setProperty("max-width", "min(250px, 100%)", "important");
        this.imageRule.style.setProperty("max-height", "200px", "important");
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
        const styleSheet = this.baseStyle.sheet as CSSStyleSheet;
        const firstRule = styleSheet.cssRules[0] as CSSStyleRule;
        return firstRule.style.direction === "rtl";
    }
}
