// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import { noop } from "lodash-es";

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
    const srcs = [...fragment.querySelectorAll<HTMLImageElement>("img[src]")].map(
        (img) => img.src,
    );
    return srcs;
}

function createImage(src: string, async: boolean): HTMLImageElement {
    const img = new Image();
    img.src = src;
    if (async) {
        img.decoding = "async";
        img.decode().catch(noop);
    }
    return img;
}

export function preloadAnswerImages(html: string, async: boolean): void {
    template.innerHTML = html;
    extractImageSrcs(template.content).forEach((src) => createImage(src, async));
}

/** Prevent flickering & layout shift on image load */
export function preloadImages(fragment: DocumentFragment, async: boolean): Promise<void>[] {
    return extractImageSrcs(fragment).map((src) => createImage(src, async)).map(imageLoaded);
}
