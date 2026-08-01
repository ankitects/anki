// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { preloadImages } from "./images";

const template = document.createElement("template");
const htmlDoc = document.implementation.createHTMLDocument();
const fontURLPattern = /url\s*\(\s*(?<quote>["']?)(?<url>\S.*?)\k<quote>\s*\)/g;
const cachedFonts = new Set<string>();

type CSSElement = HTMLStyleElement | HTMLLinkElement;

function loadResource(element: HTMLElement): Promise<void> {
    return new Promise((resolve) => {
        function resolveAndRemove(): void {
            resolve();
            document.head.removeChild(element);
        }
        element.addEventListener("load", resolveAndRemove);
        element.addEventListener("error", resolveAndRemove);
        document.head.appendChild(element);
    });
}

function createPreloadLink(href: string, as: string): HTMLLinkElement {
    const link = document.createElement("link");
    link.rel = "preload";
    link.href = href;
    link.as = as;
    if (as === "font") {
        link.crossOrigin = "";
    }
    return link;
}

function extractExternalStyleSheets(fragment: DocumentFragment): CSSElement[] {
    return [...fragment.querySelectorAll<CSSElement>("style, link")]
        .filter((css) =>
            (css instanceof HTMLStyleElement && css.innerHTML.includes("@import"))
            || (css instanceof HTMLLinkElement && css.rel === "stylesheet")
        );
}

/** Prevent FOUC */
function preloadStyleSheets(fragment: DocumentFragment): Promise<void>[] {
    const promises = extractExternalStyleSheets(fragment).map((css) => {
        // prevent the CSS from affecting the page rendering
        css.media = "print";

        return loadResource(css);
    });
    return promises;
}

function extractFontFaceRules(style: HTMLStyleElement): CSSFontFaceRule[] {
    htmlDoc.head.innerHTML = "";
    // must be attached to an HTMLDocument to access 'sheet' property
    htmlDoc.head.appendChild(style);

    const fontFaceRules: CSSFontFaceRule[] = [];
    if (style.sheet) {
        for (const rule of style.sheet.cssRules) {
            if (rule instanceof CSSFontFaceRule) {
                fontFaceRules.push(rule);
            }
        }
    }
    return fontFaceRules;
}

function extractFontURLs(rule: CSSFontFaceRule): string[] {
    const src = rule.style.getPropertyValue("src");
    const matches = src.matchAll(fontURLPattern);
    return [...matches].map((m) => (m.groups?.url ? m.groups.url : "")).filter(Boolean);
}

function preloadFonts(fragment: DocumentFragment): Promise<void>[] {
    const styles = fragment.querySelectorAll("style");
    const fonts: string[] = [];
    for (const style of styles) {
        for (const rule of extractFontFaceRules(style)) {
            fonts.push(...extractFontURLs(rule));
        }
    }
    const newFonts = fonts.filter((font) => !cachedFonts.has(font));
    newFonts.forEach((font) => cachedFonts.add(font));
    const promises = newFonts.map((font) => {
        const link = createPreloadLink(font, "font");
        return loadResource(link);
    });
    return promises;
}

export async function preloadResources(html: string): Promise<void> {
    template.innerHTML = html;
    const fragment = template.content;
    const styleSheets = preloadStyleSheets(fragment.cloneNode(true) as DocumentFragment);
    const images = preloadImages(fragment.cloneNode(true) as DocumentFragment);
    const fonts = preloadFonts(fragment.cloneNode(true) as DocumentFragment);

    let timeout: number;
    if (fonts.length) {
        timeout = 800;
    } else if (styleSheets.length) {
        timeout = 500;
    } else if (images.length) {
        timeout = 200;
    } else {
        return;
    }

    await Promise.race([
        Promise.all([...styleSheets, ...images, ...fonts]),
        new Promise((r) => setTimeout(r, timeout)),
    ]);
}
