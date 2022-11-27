// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

type CssElementType = HTMLStyleElement | HTMLLinkElement;

const preloadCssClassName = "preload-css";
const template = document.createElement("template");

export async function maybePreloadExternalCss(html: string): Promise<void> {
    clearPreloadedCss();
    template.innerHTML = html;
    const externalCssElements = extractExternalCssElements(template.content);
    if (externalCssElements.length) {
        await Promise.race([
            Promise.all(externalCssElements.map(injectAndLoadCss)),
            new Promise((r) => setTimeout(r, 500)),
        ]);
    }
}

function clearPreloadedCss(): void {
    [...document.head.getElementsByClassName(preloadCssClassName)].forEach((css) => css.remove());
}

function extractExternalCssElements(fragment: DocumentFragment): CssElementType[] {
    return <CssElementType[]> (
        [...fragment.querySelectorAll("style, link")].filter(
            (css) =>
                (css instanceof HTMLStyleElement
                    && css.innerHTML.includes("@import"))
                || (css instanceof HTMLLinkElement && css.rel === "stylesheet"),
        )
    );
}

function injectAndLoadCss(css: CssElementType): Promise<void> {
    return new Promise((resolve) => {
        css.classList.add(preloadCssClassName);

        // this prevents the css from affecting the page rendering
        css.media = "print";

        css.addEventListener("load", () => resolve());
        css.addEventListener("error", () => resolve());
        document.head.appendChild(css);
    });
}
