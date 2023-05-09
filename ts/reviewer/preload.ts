// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

type CSSElement = HTMLStyleElement | HTMLLinkElement;

const template = document.createElement("template");

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

function extractExternalStyleSheets(fragment: DocumentFragment): CSSElement[] {
    return ([...fragment.querySelectorAll("style, link")] as CSSElement[])
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

export async function preloadResources(html: string): Promise<void> {
    template.innerHTML = html;
    const fragment = template.content;
    const styleSheets = preloadStyleSheets(fragment);
    if (styleSheets.length) {
        await Promise.race(
            [Promise.all(styleSheets), new Promise((r) => setTimeout(r, 500))],
        );
    }
}
