// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-non-null-assertion: "off",
 */

function loadStyleLink(container: Node, href: string): Promise<void> {
    const rootStyle = document.createElement("link");
    rootStyle.setAttribute("rel", "stylesheet");
    rootStyle.setAttribute("href", href);

    let styleResolve: () => void;
    const stylePromise = new Promise<void>((resolve) => (styleResolve = resolve));

    rootStyle.addEventListener("load", () => styleResolve());
    container.appendChild(rootStyle);

    return stylePromise;
}

function loadStyleTag(container: Node): [HTMLStyleElement, Promise<void>] {
    const style = document.createElement("style");
    style.setAttribute("rel", "stylesheet");

    let styleResolve: () => void;
    const stylePromise = new Promise<void>((resolve) => (styleResolve = resolve));

    style.addEventListener("load", () => styleResolve());
    container.appendChild(style);

    return [style, stylePromise];
}

export class EditableContainer extends HTMLDivElement {
    baseStyle: HTMLStyleElement;
    imageStyle: HTMLStyleElement;

    imagePromise: Promise<void>;
    stylePromise: Promise<void>;

    baseRule?: CSSStyleRule;

    constructor() {
        super();
        const shadow = this.attachShadow({ mode: "open" });

        if (document.documentElement.classList.contains("night-mode")) {
            this.classList.add("night-mode");
        }

        const rootPromise = loadStyleLink(shadow, "./_anki/css/editable-build.css");
        const [baseStyle, basePromise] = loadStyleTag(shadow);
        const [imageStyle, imagePromise] = loadStyleTag(shadow);

        this.baseStyle = baseStyle;
        this.imageStyle = imageStyle;

        this.imagePromise = imagePromise;
        this.stylePromise = Promise.all([
            rootPromise,
            basePromise,
            imagePromise,
        ]) as unknown as Promise<void>;
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

customElements.define("anki-editable-container", EditableContainer, { extends: "div" });
