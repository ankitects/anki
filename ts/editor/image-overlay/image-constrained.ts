// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import { removeStyleProperties } from "../../lib/styling";

function setConstrainedWidth(
    image: HTMLImageElement,
    maxWidth: number,
    maxHeight: number,
): void {
    const restrictionAspectRatio = maxWidth / maxHeight;

    const naturalWidth = image.naturalWidth;
    const naturalHeight = image.naturalHeight;
    const aspectRatio = naturalWidth / naturalHeight;

    let constrainedWidth: number;

    if (restrictionAspectRatio - aspectRatio > 1) {
        // Constrained by height
        constrainedWidth = (maxHeight / naturalHeight) * naturalWidth;
    } else {
        // Square or constrained by width
        constrainedWidth = maxWidth;
    }

    const width = Number(image.getAttribute("width")) || image.width;

    if (constrainedWidth >= width) {
        // Image was resized to be smaller than the constrained size would be
        constrainedWidth = width;
    }

    image.style.setProperty("--editor-width", `${Math.round(constrainedWidth)}px`);
}

export function setConstrainedSize(
    image: HTMLImageElement,
    maxWidth: number,
    maxHeight: number,
): void {
    image.dataset.editorSizeConstrained = "true";
    setConstrainedWidth(image, maxWidth, maxHeight);
}

export function setActualSize(image: HTMLImageElement): void {
    delete image.dataset.editorSizeConstrained;
    removeStyleProperties(image, "--editor-width");
}
