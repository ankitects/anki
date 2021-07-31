// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

function injectPreloadLink(href: string, as: string): void {
    const link = document.createElement("link");
    link.rel = "preload";
    link.href = href;
    link.as = as;
    document.head.appendChild(link);
}

export function allImagesLoaded(): Promise<void[]> {
    return Promise.all(
        Array.from(document.getElementsByTagName("img")).map(imageLoaded)
    );
}

function imageLoaded(img: HTMLImageElement): Promise<void> {
    return img.complete
        ? Promise.resolve()
        : new Promise((resolve) => {
              img.addEventListener("load", () => resolve());
              img.addEventListener("error", () => resolve());
          });
}

function clearPreloadLinks(): void {
    document.head
        .querySelectorAll("link[rel='preload']")
        .forEach((link) => link.remove());
}

function extractImageSrcs(html: string): string[] {
    const fragment = document.createRange().createContextualFragment(html);
    const srcs = [...fragment.querySelectorAll("img[src]")].map(
        (img) => (img as HTMLImageElement).src
    );
    return srcs;
}

export function preloadAnswerImages(qHtml: string, aHtml: string): void {
    clearPreloadLinks();
    const aSrcs = extractImageSrcs(aHtml);
    if (aSrcs.length) {
        const qSrcs = extractImageSrcs(qHtml);
        const diff = aSrcs.filter((src) => !qSrcs.includes(src));
        diff.forEach((src) => injectPreloadLink(src, "image"));
    }
}
