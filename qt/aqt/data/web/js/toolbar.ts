// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

/* eslint
@typescript-eslint/no-unused-vars: "off",
*/

enum SyncState {
    NoChanges = 0,
    Normal,
    Full,
}

function updateSyncColor(state: SyncState) {
    const elem = document.getElementById("sync");
    switch (state) {
        case SyncState.NoChanges:
            elem.classList.remove("full-sync", "normal-sync");
            break;
        case SyncState.Normal:
            elem.classList.add("normal-sync");
            elem.classList.remove("full-sync");
            break;
        case SyncState.Full:
            elem.classList.add("full-sync");
            elem.classList.remove("normal-sync");
            break;
    }
}

// Dealing with legacy add-ons that used CSS to absolutely position
// themselves at toolbar edges

function isAbsolutelyPositioned(node: Node): boolean {
    if (!(node instanceof HTMLElement)) {
        return false;
    }
    return getComputedStyle(node).position === "absolute";
}

function isLegacyAddonElement(node: Node): boolean {
    if (isAbsolutelyPositioned(node)) {
        return true;
    }
    for (const child of node.childNodes) {
        if (isAbsolutelyPositioned(child)) {
            return true;
        }
    }
    return false;
}

function getElementDimensions(element: HTMLElement): [number, number] {
    const widths = [element.offsetWidth];
    const heights = [element.offsetHeight];
    // Some add-ons inject spans or anchors into the toolbar whose dimensions,
    // as reported by the properties above are zero, but still occupy space due
    // to their child elements:
    for (const child of element.childNodes) {
        if (!(child instanceof HTMLElement)) {
            continue;
        }
        widths.push(child.offsetWidth);
        heights.push(child.offsetHeight);
    }
    return [Math.max(...widths), Math.max(...heights)];
}

function moveLegacyAddonsToTray() {
    const rightTray = document.getElementsByClassName("right-tray")[0];
    const toolbarChildren = document.querySelectorAll<HTMLElement>(".toolbar > *");
    const legacyAddonElements: HTMLElement[] = Array.from(toolbarChildren)
        .reverse() // restore original add-on load order
        .filter(isLegacyAddonElement);

    for (const element of legacyAddonElements) {
        const wrapperElement = document.createElement("div");
        const dimensions = getElementDimensions(element);
        element.style.right = "0px"; // remove manual padding
        wrapperElement.append(element);
        wrapperElement.style.cssText = `\
width: ${dimensions[0]}px; height: ${dimensions[1]}}px;
margin-left: 5px; margin-right: 5px; position: relative;`;
        wrapperElement.className = "tray-item tray-item-legacy";
        rightTray.append(wrapperElement);
    }
}

document.addEventListener("DOMContentLoaded", moveLegacyAddonsToTray);
