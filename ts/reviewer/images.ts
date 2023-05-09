// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

const template = document.createElement("template");

export function allImagesLoaded(): Promise<void[]> {
    return Promise.all(
        Array.from(document.getElementsByTagName("img")).map(imageLoaded),
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

function extractImageSrcs(fragment: DocumentFragment): string[] {
    const srcs = [...fragment.querySelectorAll("img[src]")].map(
        (img) => (img as HTMLImageElement).src,
    );
    return srcs;
}

function createImage(src: string): HTMLImageElement {
    const img = new Image();
    img.src = src;
    return img;
}

export function preloadAnswerImages(html: string): void {
    template.innerHTML = html;
    extractImageSrcs(template.content).forEach(createImage);
}

/** Prevent flickering & layout shift on image load */
export function preloadImages(fragment: DocumentFragment): Promise<void>[] {
    return extractImageSrcs(fragment).map(createImage).map(imageLoaded);
}
